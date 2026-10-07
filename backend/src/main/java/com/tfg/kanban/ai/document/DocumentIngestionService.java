package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.AiProperties;
import com.tfg.kanban.ai.AiUnavailableException;
import com.tfg.kanban.ai.ProcessingStage;
import com.tfg.kanban.common.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Del fichero subido al contexto que se le manda al modelo.
 *
 * <pre>
 *  fichero -&gt; validar (Tika detecta el formato real) -&gt; extraer en local
 *          -&gt; texto + tablas + candidatos visuales
 *          -&gt; solo si hace falta: DeepSeek lee esas imagenes
 *          -&gt; contexto unificado
 * </pre>
 *
 * <p>La regla es que DeepSeek no ve nada que se pueda resolver aqui: un PDF con
 * texto o un Word con tablas no hacen ninguna llamada. Solo se llama cuando una
 * pagina no tiene texto (escaneada) o cuando una imagen incrustada ha pasado el
 * filtro local de relevancia.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    /** Menos letras y cifras que esto en todo el documento y no hay nada que planificar. */
    private static final int MIN_USEFUL_CHARS = 10;

    private static final String TRUNCATED_NOTICE = "\n\n[...documento recortado por longitud...]";

    private final UploadValidator validator;
    private final PdfExtractor pdfExtractor;
    private final DocxExtractor docxExtractor;
    private final PlainTextExtractor plainTextExtractor;
    private final VisionAnalyzer visionAnalyzer;
    private final ProcessedDocumentCache cache;
    private final AiProperties properties;

    /** El procesamiento completo, con vision si hace falta. Falla con un motivo claro si no hay nada que leer. */
    public ProcessedDocument ingest(MultipartFile file, ProgressListener progress) {
        progress.stage(ProcessingStage.ANALYZING);
        ValidatedUpload upload = validator.validate(file);

        Optional<ProcessedDocument> cached = cache.get(upload.sha256());
        if (cached.isPresent()) {
            log.info("Documento {} ya procesado: se reutiliza", upload.filename());
            return cached.get();
        }

        progress.stage(ProcessingStage.EXTRACTING);
        RawDocument raw = extract(upload);

        List<String> warnings = new ArrayList<>(raw.warnings());
        List<VisionAnalyzer.Finding> findings = readVisuals(raw, warnings, progress);

        ProcessedDocument document = assemble(raw, findings, warnings);
        ensureUseful(raw, document);
        cache.put(upload.sha256(), document);
        return document;
    }

    /**
     * Solo lo que se lee en local, sin llamar a nadie y sin quejarse de que falte
     * texto: para buscar una fecha de inicio no merece la pena pagar la lectura de un
     * escaneado, y si no hay texto simplemente no hay fecha.
     */
    public String localContext(MultipartFile file) {
        RawDocument raw = extract(validator.validate(file));
        return assemble(raw, List.of(), new ArrayList<>(raw.warnings())).context();
    }

    private RawDocument extract(ValidatedUpload upload) {
        return switch (upload.format()) {
            case PDF -> pdfExtractor.extract(upload);
            case DOCX -> docxExtractor.extract(upload);
            case TEXT -> plainTextExtractor.extract(upload);
        };
    }

    // ------------------------------------------------------------ lo visual

    private List<VisionAnalyzer.Finding> readVisuals(RawDocument raw, List<String> warnings, ProgressListener progress) {
        if (raw.visuals().isEmpty()) return List.of();

        List<VisualCandidate> scanned = raw.visuals().stream()
                .filter(visual -> visual.kind() == VisualCandidate.Kind.SCANNED_PAGE).toList();
        List<VisualCandidate> optional = raw.visuals().stream()
                .filter(visual -> visual.kind() != VisualCandidate.Kind.SCANNED_PAGE).toList();

        // Con todo el modulo de IA apagado tampoco se mira ninguna imagen: seria una llamada a DeepSeek.
        if (!properties.isEnabled() || !properties.getDocument().isVisionEnabled()) {
            if (!scanned.isEmpty()) {
                throw new BusinessRuleException(("El documento tiene %d páginas sin texto (escaneadas) y el análisis "
                        + "visual está desactivado en el servidor. Sube una versión con texto.").formatted(scanned.size()));
            }
            warnings.add("Se han omitido %d imágenes o tablas difíciles porque el análisis visual está desactivado."
                    .formatted(optional.size()));
            return List.of();
        }

        progress.stage(ProcessingStage.VISUAL);
        List<VisionAnalyzer.Finding> findings = new ArrayList<>();

        // Una pagina escaneada no tiene otra lectura: si DeepSeek falla, falla todo y se dice por que.
        if (!scanned.isEmpty()) {
            findings.addAll(visionAnalyzer.analyze(scanned));
            Set<Integer> read = findings.stream().map(VisionAnalyzer.Finding::page).collect(Collectors.toSet());
            String unread = scanned.stream().map(VisualCandidate::page).filter(page -> !read.contains(page))
                    .map(String::valueOf).collect(Collectors.joining(", "));
            if (!unread.isBlank()) {
                warnings.add("No se ha podido leer el contenido de las páginas: " + unread + ".");
            }
        }
        // Una imagen o una tabla dudosa si: el documento ya tiene su texto, esto solo lo mejora.
        if (!optional.isEmpty()) {
            try {
                findings.addAll(visionAnalyzer.analyze(optional));
            } catch (AiUnavailableException ex) {
                log.warn("Vision opcional fallida: {}", ex.getMessage());
                warnings.add("No se han podido analizar las imágenes del documento: " + ex.getMessage());
            }
        }
        return findings;
    }

    // ------------------------------------------------------------ ensamblado

    private ProcessedDocument assemble(RawDocument raw, List<VisionAnalyzer.Finding> findings, List<String> warnings) {
        AiProperties.Document config = properties.getDocument();

        String text = raw.text();
        if (text.length() > config.getMaxTextChars()) {
            log.info("Documento {} recortado de {} a {} caracteres", raw.filename(), text.length(), config.getMaxTextChars());
            text = text.substring(0, config.getMaxTextChars()) + TRUNCATED_NOTICE;
            warnings.add("El documento es muy largo: el enunciado se ha recortado a %,d caracteres."
                    .formatted(config.getMaxTextChars()));
        }

        List<ExtractedTable> tables = new ArrayList<>();
        int tablesChars = 0;
        for (int i = 0; i < raw.tables().size(); i++) {
            ExtractedTable table = raw.tables().get(i);
            if (tablesChars + table.markdown().length() > config.getMaxTablesChars()) {
                text = text.replace("[Tabla %d]".formatted(i + 1), "[Tabla %d omitida por longitud]".formatted(i + 1));
                warnings.add("La tabla %d se ha omitido: las tablas ya ocupaban el máximo.".formatted(i + 1));
                // Las siguientes mantienen su numero, asi que el hueco se rellena con un marcador.
                tables.add(new ExtractedTable(table.page(), "(omitida por longitud)"));
                continue;
            }
            tablesChars += table.markdown().length();
            tables.add(table);
        }

        List<VisionAnalyzer.Finding> visual = new ArrayList<>();
        int visualChars = 0;
        for (VisionAnalyzer.Finding finding : findings) {
            if (visualChars + finding.content().length() > config.getMaxVisualChars()) {
                warnings.add("Parte del contenido visual se ha omitido por longitud.");
                break;
            }
            visualChars += finding.content().length();
            visual.add(finding);
        }

        int read = (int) findings.stream().filter(f -> f.kind() == VisualCandidate.Kind.SCANNED_PAGE).count();
        return new ProcessedDocument(raw.filename(), raw.format(), raw.sizeBytes(), raw.pages(), raw.title(),
                text, tables, visual, read, raw.blankPages(), List.copyOf(warnings));
    }

    /** No se manda un documento vacio al modelo: se dice por que lo esta. */
    private void ensureUseful(RawDocument raw, ProcessedDocument document) {
        int useful = TextCleaner.meaningfulChars(document.text())
                + document.tables().stream().mapToInt(t -> TextCleaner.meaningfulChars(t.markdown())).sum()
                + document.visual().stream().mapToInt(f -> TextCleaner.meaningfulChars(f.content())).sum();
        if (useful >= MIN_USEFUL_CHARS) return;

        if (raw.pages() > 0 && raw.blankPages() == raw.pages()) {
            throw new BusinessRuleException("El documento tiene %d páginas y todas están en blanco".formatted(raw.pages()));
        }
        boolean hadVisuals = raw.visuals().stream().anyMatch(v -> v.kind() == VisualCandidate.Kind.SCANNED_PAGE);
        if (hadVisuals) {
            throw new BusinessRuleException("El documento parece escaneado y el modelo no ha podido leer su contenido. "
                    + "Prueba con una versión de más calidad o con texto seleccionable.");
        }
        throw new BusinessRuleException(
                "El documento no contiene texto legible ni imágenes con información que se puedan leer.");
    }
}
