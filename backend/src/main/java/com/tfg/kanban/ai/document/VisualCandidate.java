package com.tfg.kanban.ai.document;

/**
 * Algo del documento que no se puede leer como texto y que quiza haya que
 * mirar: ya reducido y codificado como JPEG, listo para mandarse.
 *
 * @param page 1-based; 0 si el formato no tiene paginas (Word)
 */
public record VisualCandidate(Kind kind, int page, byte[] jpeg, int width, int height) {

    public enum Kind {
        /** Pagina entera sin texto: hay que transcribirla. */
        SCANNED_PAGE,
        /** Pagina con una tabla que el analisis local no ha podido reconstruir con confianza. */
        TABLE_PAGE,
        /** Imagen incrustada que puede aportar informacion (diagrama, captura, esquema). */
        EMBEDDED_IMAGE
    }
}
