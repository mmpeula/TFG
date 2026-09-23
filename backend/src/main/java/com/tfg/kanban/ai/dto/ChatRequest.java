package com.tfg.kanban.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Pregunta al asistente sobre el workspace", example = """
        {"message":"Que deberia hacer primero?",
         "history":[{"role":"user","content":"Resume lo pendiente"},
                    {"role":"assistant","content":"Quedan 5 tareas..."}]}
        """)
public record ChatRequest(

        @NotBlank(message = "escribe una pregunta")
        @Size(max = 2000, message = "la pregunta es demasiado larga")
        String message,

        @Schema(description = "Turnos anteriores, para que entienda las preguntas encadenadas")
        List<ChatTurn> history
) {

    public List<ChatTurn> historyOrEmpty() {
        return history == null ? List.of() : history;
    }
}
