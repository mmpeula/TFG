package com.tfg.kanban.task;

import com.tfg.kanban.domain.model.ActivityAction;
import com.tfg.kanban.domain.model.ActivityLog;
import com.tfg.kanban.domain.model.Task;
import com.tfg.kanban.domain.repository.ActivityLogRepository;
import com.tfg.kanban.domain.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Pone fecha de fin a las tareas que ya estaban terminadas antes de que existiera el
 * campo ({@code Task.completedAt}).
 *
 * <p>La fecha sale del historial de actividad: el ultimo «Movió la tarea … a 'Hecho'» (o
 * «Creó la tarea … en 'Hecho'», si nacio ya terminada) dice cuando paso a terminada de
 * verdad. Si el historial no lo tiene, se usa su ultimo cambio, que es la mejor
 * aproximacion que queda.</p>
 *
 * <p>Solo mira las que estan en la ultima columna y siguen sin fecha: desde que existe el
 * campo, toda tarea que entra ahi ya la lleva, asi que en los arranques siguientes no
 * encuentra ninguna y no hace nada.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompletedAtBackfill implements ApplicationRunner {

    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Task> sinFecha = taskRepository.findDoneWithoutCompletedAt();
        // Directamente en la base de datos y no sobre la entidad: asi su «ultimo cambio» no pasa a ser hoy.
        sinFecha.forEach(task -> taskRepository.updateCompletedAt(task.getId(), whenItWasDone(task)));
        if (!sinFecha.isEmpty()) {
            log.info("Fecha de fin puesta a {} tareas que ya estaban terminadas", sinFecha.size());
        }
    }

    /** El ultimo paso a la columna de terminadas que quedo en el historial; si no hay ninguno, su ultimo cambio. */
    Instant whenItWasDone(Task task) {
        String columna = task.getBoardColumn().getName();
        String movida = "a '%s'".formatted(columna);
        String creada = "en '%s'".formatted(columna);
        return activityLogRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Task", task.getId()).stream()
                .filter(entry -> entry.getDetails() != null
                        && (entry.getAction() == ActivityAction.MOVIMIENTO && entry.getDetails().endsWith(movida)
                        || entry.getAction() == ActivityAction.CREACION && entry.getDetails().endsWith(creada)))
                .map(ActivityLog::getOccurredAt)
                .findFirst()
                .orElse(task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt());
    }
}
