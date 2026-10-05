package com.tfg.kanban.board.dto;

import com.tfg.kanban.domain.model.BoardColumn;
import com.tfg.kanban.task.dto.TaskResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Columna del tablero con sus tareas", example = """
        {"id":10,"name":"Por hacer","position":0,"taskCount":3,"tasks":[]}
        """)
public record ColumnResponse(
        Long id,
        String name,
        Integer position,
        int taskCount,
        @Schema(description = "Tareas ordenadas por posicion; null si la respuesta no las incluye")
        List<TaskResponse> tasks
) {

    /** Version ligera, sin cargar las tareas. */
    public static ColumnResponse summary(BoardColumn column, int taskCount) {
        return new ColumnResponse(column.getId(), column.getName(), column.getPosition(),
                taskCount, null);
    }

    /** Version completa usada por el tablero Kanban. */
    public static ColumnResponse withTasks(BoardColumn column, List<TaskResponse> tasks) {
        return new ColumnResponse(column.getId(), column.getName(), column.getPosition(),
                tasks.size(), tasks);
    }
}
