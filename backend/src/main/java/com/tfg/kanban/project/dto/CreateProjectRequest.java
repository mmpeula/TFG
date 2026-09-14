package com.tfg.kanban.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Alta de un proyecto (practica de una asignatura)", example = """
        {"teamId":1,"name":"Practica 2 - Bases de datos","description":"Modelo E/R y SQL",
         "briefing":"Enunciado completo de la practica...","deadline":"2026-12-15"}
        """)
public record CreateProjectRequest(

        @NotNull(message = "el equipo es obligatorio")
        Long teamId,

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 150)
        String name,

        @Size(max = 1000)
        String description,

        @Schema(description = "Guion de la practica; sera la entrada del modulo de IA")
        String briefing,

        @Schema(description = "Fecha de entrega", example = "2026-12-15")
        LocalDate deadline
) {
}
