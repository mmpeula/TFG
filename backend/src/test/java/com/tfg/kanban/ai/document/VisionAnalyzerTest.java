package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.AiProperties;
import com.tfg.kanban.ai.DeepSeekClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VisionAnalyzerTest {

    private final DeepSeekClient deepSeek = mock(DeepSeekClient.class);
    private final AiProperties properties = new AiProperties();
    private VisionAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new VisionAnalyzer(deepSeek, properties);
    }

    private static VisualCandidate candidate(VisualCandidate.Kind kind, int page) {
        return new VisualCandidate(kind, page, new byte[] {1, 2, 3}, 10, 10);
    }

    @Test
    @DisplayName("Sin nada que mirar no se llama a DeepSeek")
    void doesNothingWithoutImages() {
        assertThat(analyzer.analyze(List.of())).isEmpty();
        verifyNoInteractions(deepSeek);
    }

    @Test
    @DisplayName("Las imagenes viajan en lotes: cinco imagenes con lotes de cuatro son dos llamadas, no cinco")
    void batchesImages() {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt())).thenReturn("IRRELEVANTE");
        List<VisualCandidate> five = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> candidate(VisualCandidate.Kind.EMBEDDED_IMAGE, i + 1)).toList();

        analyzer.analyze(five);

        ArgumentCaptor<List<DeepSeekClient.ImagePart>> images = ArgumentCaptor.forClass(List.class);
        verify(deepSeek, times(2)).chatVision(anyString(), anyString(), images.capture(), anyInt());
        assertThat(images.getAllValues()).extracting(List::size).containsExactly(4, 1);
    }

    @Test
    @DisplayName("Las paginas se piden en alta calidad y como transcripcion; las imagenes sueltas, como descripcion")
    void usesTheRightTaskAndDetail() {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt())).thenReturn("IRRELEVANTE");

        analyzer.analyze(List.of(candidate(VisualCandidate.Kind.SCANNED_PAGE, 3),
                candidate(VisualCandidate.Kind.EMBEDDED_IMAGE, 4)));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<DeepSeekClient.ImagePart>> images = ArgumentCaptor.forClass(List.class);
        verify(deepSeek, times(2)).chatVision(system.capture(), user.capture(), images.capture(), eq(8_000));

        assertThat(system.getAllValues().get(0)).contains("transcriptor");
        assertThat(user.getAllValues().get(0)).contains("PÁGINA 3");
        assertThat(images.getAllValues().get(0).get(0).detail()).isEqualTo("high");
        assertThat(images.getAllValues().get(0).get(0).dataUrl()).startsWith("data:image/jpeg;base64,");
        assertThat(system.getAllValues().get(1)).contains("IRRELEVANTE");
        assertThat(user.getAllValues().get(1)).contains("IMAGEN 1").contains("pagina 4");
    }

    @Test
    @DisplayName("Reparte la respuesta entre las paginas por sus marcas y descarta lo irrelevante")
    void splitsAnswerByMarkers() {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt())).thenReturn("""
                === IMAGEN 1 ===
                IRRELEVANTE
                === IMAGEN 2 ===
                [Diagrama: A -> B]""");

        List<VisionAnalyzer.Finding> findings = analyzer.analyze(List.of(
                candidate(VisualCandidate.Kind.EMBEDDED_IMAGE, 1), candidate(VisualCandidate.Kind.EMBEDDED_IMAGE, 2)));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.page()).isEqualTo(2);
            assertThat(finding.content()).isEqualTo("[Diagrama: A -> B]");
        });
    }

    @Test
    @DisplayName("Una lectura enorme se recorta, no se acepta sin limite")
    void capsHugeFindings() {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt()))
                .thenReturn("=== PÁGINA 1 ===\n" + "texto ".repeat(5_000));

        List<VisionAnalyzer.Finding> findings = analyzer.analyze(List.of(candidate(VisualCandidate.Kind.SCANNED_PAGE, 1)));

        assertThat(findings.get(0).content()).hasSizeLessThan(6_100).endsWith("[...recortado]");
    }

    @Test
    @DisplayName("Una respuesta vacia de DeepSeek no produce lecturas")
    void emptyAnswerYieldsNothing() {
        when(deepSeek.chatVision(anyString(), anyString(), anyList(), anyInt())).thenReturn(null);

        assertThat(analyzer.analyze(List.of(candidate(VisualCandidate.Kind.SCANNED_PAGE, 1)))).isEmpty();
    }
}
