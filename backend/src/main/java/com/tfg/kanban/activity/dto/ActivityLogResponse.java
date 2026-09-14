package com.tfg.kanban.activity.dto;

import com.tfg.kanban.domain.model.ActivityAction;
import com.tfg.kanban.domain.model.ActivityLog;
import com.tfg.kanban.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Entrada del historial de actividad de un proyecto", example = """
        {"id":12,"action":"MOVIMIENTO","entityType":"Task","entityId":7,
         "details":"Movio la tarea 'Redactar memoria' de 'Por hacer' a 'En curso'",
         "user":{"id":1,"fullName":"Ana Garcia","email":"ana@uni.es"},
         "occurredAt":"2026-08-21T10:15:30Z"}
        """)
public record ActivityLogResponse(
        Long id,
        ActivityAction action,
        String entityType,
        Long entityId,
        String details,
        UserSummary user,
        Instant occurredAt
) {

    public static ActivityLogResponse from(ActivityLog log) {
        return new ActivityLogResponse(log.getId(), log.getAction(), log.getEntityType(), log.getEntityId(),
                log.getDetails(), UserSummary.from(log.getUser()), log.getOccurredAt());
    }
}
