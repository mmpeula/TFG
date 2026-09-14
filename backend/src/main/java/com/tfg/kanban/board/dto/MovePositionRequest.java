package com.tfg.kanban.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Nueva posicion de una columna dentro del tablero", example = "{\"position\":2}")
public record MovePositionRequest(

        @NotNull(message = "la posicion es obligatoria")
        @Min(value = 0, message = "la posicion no puede ser negativa")
        Integer position
) {
}
