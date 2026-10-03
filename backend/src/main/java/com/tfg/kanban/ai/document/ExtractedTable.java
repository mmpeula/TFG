package com.tfg.kanban.ai.document;

/** Una tabla ya en Markdown. {@code page} es 0 si el formato no tiene paginas. */
public record ExtractedTable(int page, String markdown) {
}
