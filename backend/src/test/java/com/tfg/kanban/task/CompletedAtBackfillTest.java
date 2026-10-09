package com.tfg.kanban.task;

import com.tfg.kanban.domain.model.ActivityAction;
import com.tfg.kanban.domain.model.ActivityLog;
import com.tfg.kanban.domain.model.Board;
import com.tfg.kanban.domain.model.BoardColumn;
import com.tfg.kanban.domain.model.Task;
import com.tfg.kanban.domain.repository.ActivityLogRepository;
import com.tfg.kanban.domain.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La fecha de fin de las tareas que ya estaban terminadas antes de existir el campo: la
 * del historial cuando lo dice, y si no, la mejor aproximacion.
 */
@ExtendWith(MockitoExtension.class)
class CompletedAtBackfillTest {

    private static final Instant CREADA = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant MOVIDA = Instant.parse("2026-09-10T17:30:00Z");
    private static final Instant TOCADA = Instant.parse("2026-09-20T09:00:00Z");

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;

    @InjectMocks
    private CompletedAtBackfill backfill;

    private Task task;

    @BeforeEach
    void setUp() {
        BoardColumn hecho = BoardColumn.builder().id(12L).name("Hecho").position(2).board(new Board()).build();
        task = Task.builder().id(7L).title("Memoria").boardColumn(hecho).build();
        task.setCreatedAt(CREADA);
        task.setUpdatedAt(TOCADA);
    }

    private ActivityLog entrada(ActivityAction action, String details, Instant when) {
        return ActivityLog.builder().action(action).entityType("Task").entityId(7L).details(details).occurredAt(when).build();
    }

    @Test
    @DisplayName("La fecha es la del ultimo paso a «Hecho» que quedo en el historial, no la de despues")
    void usesTheLastMoveIntoTheDoneColumn() {
        when(activityLogRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Task", 7L)).thenReturn(List.of(
                entrada(ActivityAction.ACTUALIZACION, "Actualizó la tarea 'Memoria'", TOCADA),
                entrada(ActivityAction.MOVIMIENTO, "Reordenó la tarea 'Memoria' dentro de 'Hecho'", TOCADA),
                entrada(ActivityAction.MOVIMIENTO, "Movió la tarea 'Memoria' de 'En progreso' a 'Hecho'", MOVIDA),
                entrada(ActivityAction.MOVIMIENTO, "Movió la tarea 'Memoria' de 'Hecho' a 'En progreso'", CREADA)));

        assertThat(backfill.whenItWasDone(task)).isEqualTo(MOVIDA);
    }

    @Test
    @DisplayName("Si nacio ya en «Hecho», cuenta desde que se creo")
    void usesTheCreationWhenItWasCreatedDone() {
        when(activityLogRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Task", 7L)).thenReturn(List.of(
                entrada(ActivityAction.CREACION, "Creó la tarea 'Memoria' en 'Hecho'", CREADA)));

        assertThat(backfill.whenItWasDone(task)).isEqualTo(CREADA);
    }

    @Test
    @DisplayName("Sin rastro en el historial, se usa su ultimo cambio")
    void fallsBackToTheLastChange() {
        when(activityLogRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Task", 7L)).thenReturn(List.of());

        assertThat(backfill.whenItWasDone(task)).isEqualTo(TOCADA);

        task.setUpdatedAt(null);
        assertThat(backfill.whenItWasDone(task)).isEqualTo(CREADA);
    }

    @Test
    @DisplayName("Se escribe solo en las que faltan, y sin pasar por la entidad")
    void writesOnlyTheMissingOnes() {
        when(taskRepository.findDoneWithoutCompletedAt()).thenReturn(List.of(task));
        when(activityLogRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Task", 7L)).thenReturn(List.of());

        backfill.run(null);

        verify(taskRepository).updateCompletedAt(7L, TOCADA);
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Si no falta ninguna, no toca nada")
    void doesNothingWhenNothingIsMissing() {
        when(taskRepository.findDoneWithoutCompletedAt()).thenReturn(List.of());

        backfill.run(null);

        verify(taskRepository, never()).updateCompletedAt(anyLong(), any());
    }
}
