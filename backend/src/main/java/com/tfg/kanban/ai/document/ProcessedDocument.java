package com.tfg.kanban.ai.document;

import java.util.List;

/**
 * El documento ya leido y preparado para el modelo. {@link #context()} es lo que
 * se le manda: secciones con nombre en vez de un bloque de texto, para que sepa
 * que es el enunciado, que es una tabla y que es la lectura de una imagen.
 *
 * @param text    el enunciado; las tablas aparecen como {@code [Tabla n]}
 * @param visual  lo que el modelo ha leido de paginas e imagenes, ya sin lo irrelevante
 * @param pages   0 si el formato no tiene paginas
 * @param visuallyReadPages paginas que no tenian texto y se han leido a la vista
 */
public record ProcessedDocument(
        String filename,
        DocumentFormat format,
        long sizeBytes,
        int pages,
        String title,
        String text,
        List<ExtractedTable> tables,
        List<VisionAnalyzer.Finding> visual,
        int visuallyReadPages,
        int blankPages,
        List<String> warnings
) {

    /** El contexto unificado: enunciado, tablas, contenido visual y metadatos, las secciones vacias se omiten. */
    public String context() {
        StringBuilder context = new StringBuilder();

        if (!text.isBlank()) {
            context.append("# ENUNCIADO\n\n").append(text.strip()).append("\n\n");
        }
        if (!tables.isEmpty()) {
            context.append("# TABLAS\n\n");
            for (int i = 0; i < tables.size(); i++) {
                ExtractedTable table = tables.get(i);
                context.append("## Tabla ").append(i + 1);
                if (table.page() > 0) context.append(" (pagina ").append(table.page()).append(')');
                context.append("\n\n").append(table.markdown()).append("\n\n");
            }
        }
        if (!visual.isEmpty()) {
            context.append("# CONTENIDO VISUAL\n\n")
                    .append("Lo siguiente es la lectura automatica de paginas e imagenes del documento, no su texto original.\n\n");
            for (VisionAnalyzer.Finding finding : visual) {
                context.append("## ").append(headingOf(finding)).append("\n\n")
                        .append(finding.content().strip()).append("\n\n");
            }
        }
        context.append("# METADATOS\n\n").append(metadata());
        return context.toString().strip();
    }

    private static String headingOf(VisionAnalyzer.Finding finding) {
        return switch (finding.kind()) {
            case SCANNED_PAGE -> "Pagina %d (leida visualmente: no tenia texto)".formatted(finding.page());
            case TABLE_PAGE -> "Pagina %d (releida visualmente por tener una tabla compleja)".formatted(finding.page());
            case EMBEDDED_IMAGE -> finding.page() > 0 ? "Imagen de la pagina %d".formatted(finding.page())
                    : "Imagen del documento";
        };
    }

    private String metadata() {
        StringBuilder meta = new StringBuilder();
        meta.append("- Documento: ").append(filename).append('\n');
        meta.append("- Formato: ").append(format.label()).append('\n');
        if (pages > 0) {
            meta.append("- Paginas: ").append(pages);
            if (visuallyReadPages > 0) meta.append(" (").append(visuallyReadPages).append(" leidas visualmente)");
            if (blankPages > 0) meta.append(" (").append(blankPages).append(" en blanco)");
            meta.append('\n');
        }
        meta.append("- Tamano: ").append(sizeBytes < 1024 * 1024 ? Math.max(1, sizeBytes / 1024) + " KB"
                : "%.1f MB".formatted(sizeBytes / (1024.0 * 1024.0))).append('\n');
        if (title != null) meta.append("- Titulo: ").append(title).append('\n');
        return meta.toString();
    }
}
