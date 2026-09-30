package com.tfg.kanban.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Pregunta al asistente sobre el espacio de trabajo", example = """
        {"message":"Que deberia hacer primero?",
         "history":[{"role":"user","content":"Resume lo pendiente"},
                    {"role":"assistant","content":"Quedan 5 tareas..."}]}
        """)
public record ChatRequest(

        @NotBlank(message = "escribe una pregunta")
        @Size(max = 2000, message = "la pregunta es demasiado larga")
        String message,

        @Schema(description = "Turnos anteriores, para que entienda las preguntas encadenadas")
        List<ChatTurn> history,

        @Schema(description = "Id de la tarea abierta en el panel, si se pregunta desde ahi. "
                + "El asistente entra en detalle sobre ella sin que haga falta repetirle de cual se habla")
        Long taskId
) {

    public List<ChatTurn> historyOrEmpty() {
        return history == null ? List.of() : history;
    }
}
