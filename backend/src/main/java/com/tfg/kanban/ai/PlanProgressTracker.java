package com.tfg.kanban.ai;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recuerda en que fase va la generacion en curso de cada proyecto.
 *
 * <p>Generar es una unica peticion HTTP que tarda (hasta minutos si hay que leer
 * un escaneado), asi que la respuesta no puede ir contando por donde va. La
 * interfaz pregunta aparte, cada poco, y esto es lo que contesta. Vive en memoria
 * a proposito: es un dato de segundos de vida y de una sola instancia.</p>
 */
@Component
public class PlanProgressTracker {

    /** Una generacion colgada no debe dejar una fase pintada para siempre. */
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);

    private record Entry(ProcessingStage stage, Instant since) {
    }

    private final Map<Long, Entry> stages = new ConcurrentHashMap<>();

    public void advance(Long projectId, ProcessingStage stage) {
        stages.put(projectId, new Entry(stage, Instant.now()));
    }

    public void clear(Long projectId) {
        stages.remove(projectId);
    }

    public Optional<ProcessingStage> current(Long projectId) {
        Entry entry = stages.get(projectId);
        if (entry == null) return Optional.empty();
        if (entry.since().plus(STALE_AFTER).isBefore(Instant.now())) {
            stages.remove(projectId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.stage());
    }
}
