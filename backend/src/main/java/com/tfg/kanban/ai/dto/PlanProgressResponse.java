package com.tfg.kanban.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fase de la generacion en curso; sin fase si no hay ninguna")
public record PlanProgressResponse(
        @Schema(description = "RECEIVED, ANALYZING, EXTRACTING, VISUAL o PLANNING", example = "EXTRACTING")
        String stage
) {
}
