package com.tfg.kanban.health;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Estado de la aplicacion y de su conexion con la base de datos")
public record HealthResponse(
        @Schema(example = "UP") String status,
        @Schema(example = "planifia-backend") String application,
        @Schema(example = "docker") String profile,
        Instant timestamp,
        DatabaseStatus database
) {

    @Schema(description = "Estado de la base de datos")
    public record DatabaseStatus(
            @Schema(example = "UP") String status,
            @Schema(example = "Oracle") String product,
            @Schema(example = "Oracle Database 23ai Free Release 23.0.0.0.0") String version,
            @Schema(description = "Mensaje de error si la conexion ha fallado") String error
    ) {
    }
}
