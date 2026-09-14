package com.tfg.kanban.task;

import com.tfg.kanban.common.CountByEntity;
import com.tfg.kanban.domain.model.Task;
import com.tfg.kanban.domain.repository.AttachmentRepository;
import com.tfg.kanban.domain.repository.CommentRepository;
import com.tfg.kanban.task.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Conversion de tareas a DTO.
 *
 * Los contadores de comentarios y adjuntos se resuelven con una unica consulta
 * agrupada por lote de tareas, en lugar de una consulta por tarjeta.
 */
@Component
@RequiredArgsConstructor
public class TaskMapper {

    private final CommentRepository commentRepository;
    private final AttachmentRepository attachmentRepository;

    public TaskResponse toResponse(Task task) {
        return TaskResponse.from(task,
                commentRepository.countByTask_Id(task.getId()),
                attachmentRepository.countByTask_Id(task.getId()));
    }

    public List<TaskResponse> toResponses(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Long> ids = tasks.stream().map(Task::getId).toList();
        Map<Long, Long> comments = toMap(commentRepository.countByTaskIds(ids));
        Map<Long, Long> attachments = toMap(attachmentRepository.countByTaskIds(ids));

        return tasks.stream()
                .map(task -> TaskResponse.from(task,
                        comments.getOrDefault(task.getId(), 0L),
                        attachments.getOrDefault(task.getId(), 0L)))
                .toList();
    }

    private Map<Long, Long> toMap(List<CountByEntity> counts) {
        return counts.stream().collect(Collectors.toMap(CountByEntity::entityId, CountByEntity::total));
    }
}
