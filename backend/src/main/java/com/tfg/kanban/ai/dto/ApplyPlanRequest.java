package com.tfg.kanban.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Tareas de la propuesta que el usuario acepta llevar al tablero", example = """
        {"boardId":3,"tasks":[{"title":"Modelar el esquema","reason":"...","type":"HISTORIA",
         "priority":"ALTA","estimatedHours":6,"dueDate":"2026-11-20"}]}
        """)
public record ApplyPlanRequest(

        @NotNull(message = "hay que indicar el tablero destino")
        Long boardId,

        @NotEmpty(message = "no se ha aceptado ninguna tarea")
        @Valid
        List<ProposedTask> tasks
) {
}
