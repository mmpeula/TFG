package com.tfg.kanban.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos de una columna del tablero", example = """
        {"name":"En curso","wipLimit":3}
        """)
public record ColumnRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 100)
        String name,

        @Schema(description = "Numero maximo de tareas simultaneas; null = sin limite")
        @Min(value = 1, message = "el limite WIP debe ser al menos 1")
        Integer wipLimit
) {
}
