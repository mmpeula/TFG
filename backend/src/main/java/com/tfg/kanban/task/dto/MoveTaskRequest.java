package com.tfg.kanban.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Destino de una tarea al arrastrarla en el tablero", example = """
        {"columnId":11,"position":0}
        """)
public record MoveTaskRequest(

        @NotNull(message = "la columna destino es obligatoria")
        Long columnId,

        @NotNull(message = "la posición es obligatoria")
        @Min(value = 0, message = "la posición no puede ser negativa")
        Integer position
) {
}
