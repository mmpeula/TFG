package com.tfg.kanban.task.dto;

import com.tfg.kanban.domain.model.Task;
import com.tfg.kanban.domain.model.TaskPriority;
import com.tfg.kanban.domain.model.TaskType;
import com.tfg.kanban.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Tarjeta del tablero", example = """
        {"id":7,"title":"Redactar la memoria","description":"Apartados 1 a 3","position":0,
         "type":"HISTORIA","priority":"ALTA","dueDate":"2026-12-01","estimatedHours":6.5,"aiGenerated":false,
         "columnId":11,"columnName":"En curso","boardId":3,"projectId":5,
         "assignees":[{"id":2,"fullName":"Luis Perez","email":"luis@uni.es"}],
         "createdBy":{"id":1,"fullName":"Ana Garcia","email":"ana@uni.es"},
         "commentCount":2,
         "createdAt":"2026-08-21T10:15:30Z","updatedAt":"2026-08-21T11:00:00Z"}
        """)
public record TaskResponse(
        Long id,
        String title,
        String description,
        Integer position,
        @Schema(description = "De que va la tarea; las anteriores a este campo se leen como TAREA") TaskType type,
        TaskPriority priority,
        LocalDate dueDate,
        BigDecimal estimatedHours,
        @Schema(description = "Lo que de verdad ha costado, si se ha apuntado") BigDecimal actualHours,
        @Schema(description = "true si la genero el modulo de IA") boolean aiGenerated,
        Long columnId,
        String columnName,
        Long boardId,
        Long projectId,
        @Schema(description = "Miembros del equipo responsables; vacio si no tiene ninguno") List<UserSummary> assignees,
        UserSummary createdBy,
        long commentCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static TaskResponse from(Task task, long commentCount) {
        var column = task.getBoardColumn();
        return new TaskResponse(
                task.getId(), task.getTitle(), task.getDescription(), task.getPosition(),
                task.getType() == null ? TaskType.TAREA : task.getType(),
                task.getPriority(), task.getDueDate(), task.getEstimatedHours(), task.getActualHours(),
                task.isAiGenerated(), column.getId(), column.getName(), column.getBoard().getId(),
                column.getBoard().getProject().getId(),
                UserSummary.fromAll(task.getAssignees()), UserSummary.from(task.getCreatedBy()),
                commentCount, task.getCreatedAt(), task.getUpdatedAt());
    }
}
