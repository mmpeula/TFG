package com.tfg.kanban.ai.document;

import org.springframework.stereotype.Component;

import java.util.List;

/** Texto plano, Markdown o CSV: no hay nada que extraer, solo decodificarlo bien. */
@Component
class PlainTextExtractor {

    RawDocument extract(ValidatedUpload upload) {
        String text = TextCleaner.clean(TextCleaner.decode(upload.bytes()));
        return new RawDocument(DocumentFormat.TEXT, upload.filename(), upload.bytes().length, 0, null,
                text, List.of(), List.of(), 0, 0, List.of());
    }
}
