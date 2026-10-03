package com.tfg.kanban.ai.document;

import java.util.List;

/**
 * Lo que sale de leer un documento en local, antes de decidir si hace falta
 * mirar algo con el modelo.
 *
 * @param text              el enunciado; las tablas aparecen como {@code [Tabla n]}
 * @param pages             0 si el formato no tiene paginas
 * @param blankPages        paginas sin texto ni imagenes (se ignoran)
 * @param skippedVisuals    paginas o imagenes que habria que mirar pero no caben en los limites
 * @param title             titulo del documento si trae uno; el autor se descarta a proposito
 */
public record RawDocument(
        DocumentFormat format,
        String filename,
        long sizeBytes,
        int pages,
        String title,
        String text,
        List<ExtractedTable> tables,
        List<VisualCandidate> visuals,
        int blankPages,
        int skippedVisuals,
        List<String> warnings
) {
}
