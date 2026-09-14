package com.tfg.kanban.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un turno de la conversacion")
public record ChatTurn(
        @Schema(description = "Quien habla", allowableValues = {"user", "assistant"}) String role,
        String content
) {
}
