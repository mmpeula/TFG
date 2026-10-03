package com.tfg.kanban.ai.document;

import java.util.ArrayList;
import java.util.List;

/** Una tabla como filas de celdas a una tabla de Markdown: el formato que mejor conserva filas y columnas en un prompt. */
final class MarkdownTables {

    private MarkdownTables() {
    }

    /**
     * La primera fila hace de cabecera. Se quitan las filas y columnas
     * totalmente vacias (las celdas combinadas de Word las dejan) y se escapan los
     * {@code |}. Devuelve null si no queda nada que ensenar.
     */
    static String render(List<List<String>> rows) {
        List<List<String>> useful = rows.stream()
                .map(row -> row.stream().map(MarkdownTables::cell).toList())
                .filter(row -> row.stream().anyMatch(cell -> !cell.isEmpty()))
                .toList();
        if (useful.isEmpty()) return null;

        int columns = useful.stream().mapToInt(List::size).max().orElse(0);
        List<Integer> keep = new ArrayList<>();
        for (int column = 0; column < columns; column++) {
            final int index = column;
            if (useful.stream().anyMatch(row -> index < row.size() && !row.get(index).isEmpty())) keep.add(column);
        }
        if (keep.isEmpty()) return null;

        StringBuilder markdown = new StringBuilder();
        for (int r = 0; r < useful.size(); r++) {
            List<String> row = useful.get(r);
            markdown.append('|');
            for (int column : keep) {
                markdown.append(' ').append(column < row.size() ? row.get(column) : "").append(" |");
            }
            markdown.append('\n');
            if (r == 0) {
                markdown.append('|').append(" --- |".repeat(keep.size())).append('\n');
            }
        }
        return markdown.toString().stripTrailing();
    }

    private static String cell(String value) {
        return value == null ? "" : value.replace("|", "\\|").replaceAll("\\s+", " ").trim();
    }
}
