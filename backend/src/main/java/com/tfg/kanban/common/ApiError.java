package com.tfg.kanban.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Cuerpo de respuesta unico para todos los errores de la API.
 *
 * Ejemplo:
 * <pre>
 * {
 *   "timestamp": "2026-08-21T10:15:30Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "code": "VALIDATION_ERROR",
 *   "message": "La peticion contiene campos invalidos",
 *   "path": "/api/tasks",
 *   "fieldErrors": [{"field": "title", "message": "no debe estar vacio"}]
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta de error estandar de la API")
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldValidationError> fieldErrors
) {

    public static ApiError of(int status, String error, String code, String message, String path) {
        return new ApiError(Instant.now(), status, error, code, message, path, null);
    }

    public static ApiError of(int status, String error, String code, String message, String path,
                              List<FieldValidationError> fieldErrors) {
        return new ApiError(Instant.now(), status, error, code, message, path, fieldErrors);
    }

    /** Detalle de un campo que no ha superado la validacion. */
    @Schema(description = "Error de validacion de un campo concreto")
    public record FieldValidationError(String field, String message) {
    }
}
