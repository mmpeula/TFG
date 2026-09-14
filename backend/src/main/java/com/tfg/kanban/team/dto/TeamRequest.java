package com.tfg.kanban.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos de un equipo", example = """
        {"name":"Grupo 4 - Ingenieria del Software","description":"Practicas del curso 2026/27"}
        """)
public record TeamRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 120)
        String name,

        @Size(max = 500)
        String description
) {
}
