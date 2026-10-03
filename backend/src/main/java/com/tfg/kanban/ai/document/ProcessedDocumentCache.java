package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Recuerda los documentos ya procesados, por el hash de su contenido.
 *
 * <p>Existe por una razon de coste: si el plan falla despues de leer un escaneado
 * (el modelo devuelve un JSON roto, se pierde la conexion), reintentar con el
 * mismo fichero no debe volver a pagar la lectura visual. Pocas entradas y poco
 * tiempo: es un atajo, no un almacen.</p>
 */
@Component
@RequiredArgsConstructor
class ProcessedDocumentCache {

    private record Entry(ProcessedDocument document, Instant storedAt) {
    }

    private final AiProperties properties;
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    synchronized Optional<ProcessedDocument> get(String sha256) {
        Entry entry = entries.get(sha256);
        if (entry == null) return Optional.empty();
        if (entry.storedAt().plus(properties.getDocument().getCacheTtl()).isBefore(Instant.now())) {
            entries.remove(sha256);
            return Optional.empty();
        }
        return Optional.of(entry.document());
    }

    synchronized void put(String sha256, ProcessedDocument document) {
        entries.put(sha256, new Entry(document, Instant.now()));
        while (entries.size() > Math.max(1, properties.getDocument().getCacheEntries())) {
            String eldest = entries.keySet().iterator().next();
            entries.remove(eldest);
        }
    }
}
