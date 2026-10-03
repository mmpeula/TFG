package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.AiProperties;
import com.tfg.kanban.ai.DeepSeekClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lee con el modelo lo que no se puede leer como texto.
 *
 * <p>Es el unico sitio de {@code ai.document} que llama a DeepSeek, y lo hace en
 * lotes: varias imagenes por peticion, no una llamada por imagen. Hay dos tareas
 * distintas, y cada una con su prompt: <b>transcribir</b> una pagina entera
 * (escaneada, o con una tabla que no se ha podido reconstruir) y <b>describir</b>
 * una imagen incrustada, decidiendo de paso si aporta algo o es un adorno.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisionAnalyzer {

    private static final String TRANSCRIBE_PROMPT = load("prompts/vision-transcribe.md");
    private static final String DESCRIBE_PROMPT = load("prompts/vision-describe.md");

    private static final Pattern MARKER = Pattern.compile("(?m)^=== (?:P[AÁ]GINA|IMAGEN) (\\d+) ===[ \\t]*$");
    private static final int MAX_FINDING_CHARS = 6_000;

    /** Lo que el modelo ha leido de una imagen que merecia la pena. */
    public record Finding(VisualCandidate.Kind kind, int page, String content) {
    }

    private final DeepSeekClient deepSeekClient;
    private final AiProperties properties;

    private static String load(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se ha podido cargar " + path, ex);
        }
    }

    /**
     * Lanza {@link com.tfg.kanban.ai.AiUnavailableException} si DeepSeek falla: quien
     * llama decide si eso es un error (una pagina escaneada no tiene otra lectura) o
     * solo un aviso (una imagen de adorno que se queda sin describir).
     */
    public List<Finding> analyze(List<VisualCandidate> candidates) {
        List<VisualCandidate> pages = candidates.stream()
                .filter(candidate -> candidate.kind() != VisualCandidate.Kind.EMBEDDED_IMAGE).toList();
        List<VisualCandidate> images = candidates.stream()
                .filter(candidate -> candidate.kind() == VisualCandidate.Kind.EMBEDDED_IMAGE).toList();

        List<Finding> findings = new ArrayList<>();
        findings.addAll(run(pages, true));
        findings.addAll(run(images, false));
        return findings;
    }

    private List<Finding> run(List<VisualCandidate> candidates, boolean transcribe) {
        List<Finding> findings = new ArrayList<>();
        int batchSize = Math.max(1, properties.getDocument().getImagesPerRequest());
        for (int from = 0; from < candidates.size(); from += batchSize) {
            List<VisualCandidate> batch = candidates.subList(from, Math.min(candidates.size(), from + batchSize));
            findings.addAll(runBatch(batch, from, transcribe));
        }
        return findings;
    }

    private List<Finding> runBatch(List<VisualCandidate> batch, int offset, boolean transcribe) {
        List<Integer> ids = new ArrayList<>();
        StringBuilder request = new StringBuilder(transcribe
                ? "Transcribe estas %d paginas, en este orden:\n".formatted(batch.size())
                : "Analiza estas %d imagenes, en este orden:\n".formatted(batch.size()));
        List<DeepSeekClient.ImagePart> parts = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            VisualCandidate candidate = batch.get(i);
            int id = transcribe ? candidate.page() : offset + i + 1;
            ids.add(id);
            request.append(transcribe ? "- PÁGINA %d%n".formatted(id)
                    : "- IMAGEN %d%s%n".formatted(id, candidate.page() > 0 ? " (de la pagina %d)".formatted(candidate.page()) : ""));
            parts.add(new DeepSeekClient.ImagePart("data:image/jpeg;base64,"
                    + Base64.getEncoder().encodeToString(candidate.jpeg()), transcribe ? "high" : "auto"));
        }

        log.info("Vision: {} imagen(es) a DeepSeek ({})", batch.size(), transcribe ? "transcripcion" : "descripcion");
        String answer = deepSeekClient.chatVision(transcribe ? TRANSCRIBE_PROMPT : DESCRIBE_PROMPT,
                request.toString(), parts, properties.getDocument().getVisionMaxOutputTokens());
        return interpret(batch, ids, answer);
    }

    /**
     * Reparte la respuesta entre las imagenes del lote por sus marcas. Si el modelo
     * no las ha puesto, no se pierde lo que ha leido: con una sola imagen es
     * todo para ella, y con varias se guarda junto, avisando de que es una lectura conjunta.
     */
    private List<Finding> interpret(List<VisualCandidate> batch, List<Integer> ids, String answer) {
        String text = answer == null ? "" : answer.strip();
        Map<Integer, String> byId = new HashMap<>();
        Matcher matcher = MARKER.matcher(text);
        List<int[]> marks = new ArrayList<>();
        while (matcher.find()) {
            marks.add(new int[] {Integer.parseInt(matcher.group(1)), matcher.start(), matcher.end()});
        }
        for (int m = 0; m < marks.size(); m++) {
            int end = m + 1 < marks.size() ? marks.get(m + 1)[1] : text.length();
            byId.merge(marks.get(m)[0], text.substring(marks.get(m)[2], end).strip(), (a, b) -> a + "\n" + b);
        }

        List<Finding> findings = new ArrayList<>();
        if (byId.isEmpty()) {
            if (batch.size() > 1 && !isIrrelevant(text) && !text.isBlank()) {
                String label = batch.stream().map(c -> c.page() > 0 ? String.valueOf(c.page()) : "?")
                        .collect(Collectors.joining(", "));
                findings.add(finding(batch.get(0), "[Lectura conjunta de las imagenes de las paginas " + label + "]\n" + text));
            } else if (batch.size() == 1) {
                addIfRelevant(findings, batch.get(0), text);
            }
            return findings;
        }
        for (int i = 0; i < batch.size(); i++) {
            addIfRelevant(findings, batch.get(i), byId.get(ids.get(i)));
        }
        return findings;
    }

    private void addIfRelevant(List<Finding> findings, VisualCandidate candidate, String content) {
        if (content == null || content.isBlank() || isIrrelevant(content)) return;
        findings.add(finding(candidate, content));
    }

    private static Finding finding(VisualCandidate candidate, String content) {
        String clean = TextCleaner.clean(content);
        if (clean.length() > MAX_FINDING_CHARS) clean = clean.substring(0, MAX_FINDING_CHARS) + "\n[...recortado]";
        return new Finding(candidate.kind(), candidate.page(), clean);
    }

    private static boolean isIrrelevant(String content) {
        String plain = Normalizer.normalize(content, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z]", "").toUpperCase();
        return plain.equals("IRRELEVANTE");
    }
}
