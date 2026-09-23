package com.tfg.kanban.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Respuesta del asistente", example = """
        {"answer":"Empezaria por 'Modelar la base de datos'...","model":"llama3",
         "contextSummary":"8 tareas · 4 personas","generatedAt":"2026-09-02T18:04:00Z"}
        """)
public record ChatResponse(
        String answer,
        String model,
        @Schema(description = "Que datos del workspace ha visto el modelo") String contextSummary,
        Instant generatedAt
) {

    public static ChatResponse of(String answer, String model, String contextSummary) {
        return new ChatResponse(answer, model, contextSummary, Instant.now());
    }
}
