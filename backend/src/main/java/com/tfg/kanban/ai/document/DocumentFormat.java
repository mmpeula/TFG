package com.tfg.kanban.ai.document;

/** Los formatos en los que suele venir el guion de una practica. */
public enum DocumentFormat {
    PDF("PDF"),
    DOCX("Word (.docx)"),
    TEXT("texto plano");

    private final String label;

    DocumentFormat(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
