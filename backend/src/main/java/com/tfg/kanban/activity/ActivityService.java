package com.tfg.kanban.activity;

import com.tfg.kanban.activity.dto.ActivityLogResponse;
import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.domain.model.ActivityAction;
import com.tfg.kanban.domain.model.ActivityLog;
import com.tfg.kanban.domain.model.Project;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.ActivityLogRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Registro del historial de actividad.
 *
 * Cada operacion relevante del dominio (crear/editar/borrar/mover tareas, comentar,
 * generar con IA, lo que hace soporte...) llama a {@link #record}. Es lo que se ve en
 * «Actividad reciente» de los ajustes del espacio y lo que fecha su ultimo movimiento.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    /** Guarda una entrada atribuida al usuario autenticado (o sin usuario si es un proceso automatico). */
    @Transactional
    public void record(Project project, ActivityAction action, String entityType, Long entityId, String details) {
        User author = SecurityUtils.currentUserId()
                .flatMap(userRepository::findById)
                .orElse(null);

        activityLogRepository.save(ActivityLog.builder()
                .project(project)
                .user(author)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(truncate(details))
                .occurredAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> findByProject(Long projectId, Pageable pageable) {
        return PageResponse.of(
                activityLogRepository.findByProject_IdOrderByOccurredAtDesc(projectId, pageable),
                ActivityLogResponse::from);
    }

    private String truncate(String details) {
        if (details == null || details.length() <= 1000) {
            return details;
        }
        return details.substring(0, 997) + "...";
    }
}
