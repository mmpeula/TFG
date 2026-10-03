package com.tfg.kanban.ai.document;

/** Una subida que ha pasado la validacion: su contenido es de verdad lo que dice ser. */
public record ValidatedUpload(byte[] bytes, String filename, DocumentFormat format, String mimeType, String sha256) {
}
