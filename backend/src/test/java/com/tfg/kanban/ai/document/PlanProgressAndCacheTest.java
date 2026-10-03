package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.AiProperties;
import com.tfg.kanban.ai.PlanProgressTracker;
import com.tfg.kanban.ai.ProcessingStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanProgressAndCacheTest {

    private static ProcessedDocument document(String name) {
        return new ProcessedDocument(name, DocumentFormat.TEXT, 10, 0, null, "texto del documento " + name,
                List.of(), List.of(), 0, 0, List.of());
    }

    @Test
    @DisplayName("El progreso recuerda la ultima fase de cada proyecto y la olvida al limpiarla")
    void tracksStagePerProject() {
        PlanProgressTracker tracker = new PlanProgressTracker();

        tracker.advance(1L, ProcessingStage.EXTRACTING);
        tracker.advance(2L, ProcessingStage.PLANNING);
        tracker.advance(1L, ProcessingStage.VISUAL);

        assertThat(tracker.current(1L)).contains(ProcessingStage.VISUAL);
        assertThat(tracker.current(2L)).contains(ProcessingStage.PLANNING);
        assertThat(tracker.current(3L)).isEmpty();

        tracker.clear(1L);
        assertThat(tracker.current(1L)).isEmpty();
        assertThat(tracker.current(2L)).isPresent();
    }

    @Test
    @DisplayName("La cache expulsa lo mas antiguo al llenarse")
    void cacheEvictsOldest() {
        AiProperties properties = new AiProperties();
        properties.getDocument().setCacheEntries(2);
        ProcessedDocumentCache cache = new ProcessedDocumentCache(properties);

        cache.put("a", document("a"));
        cache.put("b", document("b"));
        cache.get("a");
        cache.put("c", document("c"));

        assertThat(cache.get("a")).isPresent();
        assertThat(cache.get("b")).isEmpty();
        assertThat(cache.get("c")).isPresent();
    }

    @Test
    @DisplayName("La cache olvida lo que ha caducado")
    void cacheExpires() {
        AiProperties properties = new AiProperties();
        properties.getDocument().setCacheTtl(Duration.ZERO);
        ProcessedDocumentCache cache = new ProcessedDocumentCache(properties);

        cache.put("a", document("a"));

        assertThat(cache.get("a")).isEmpty();
    }

    @Test
    @DisplayName("El contexto pone las secciones en orden y omite las vacias")
    void contextSectionsInOrder() {
        ProcessedDocument document = new ProcessedDocument("guion.pdf", DocumentFormat.PDF, 2_500_000, 5, "Practica 1",
                "Texto con [Tabla 1]", List.of(new ExtractedTable(2, "| a | b |\n| --- | --- |\n| 1 | 2 |")),
                List.of(new VisionAnalyzer.Finding(VisualCandidate.Kind.EMBEDDED_IMAGE, 3, "[Diagrama: X]")),
                0, 0, List.of());

        String context = document.context();

        assertThat(context.indexOf("# ENUNCIADO")).isLessThan(context.indexOf("# TABLAS"));
        assertThat(context.indexOf("# TABLAS")).isLessThan(context.indexOf("# CONTENIDO VISUAL"));
        assertThat(context.indexOf("# CONTENIDO VISUAL")).isLessThan(context.indexOf("# METADATOS"));
        assertThat(context).contains("## Tabla 1 (pagina 2)").contains("## Imagen de la pagina 3")
                .contains("- Tamano: 2.4 MB").contains("- Titulo: Practica 1");
    }
}
