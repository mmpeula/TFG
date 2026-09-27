package com.tfg.kanban.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** Lo que se ha encontrado en el documento, sin generar nada todavia. */
@Schema(description = "Fecha de inicio detectada en el documento, o null si no se ha encontrado ninguna",
        example = "{\"startDate\":\"2026-10-01\"}")
public record DetectedDateResponse(LocalDate startDate) {
}
