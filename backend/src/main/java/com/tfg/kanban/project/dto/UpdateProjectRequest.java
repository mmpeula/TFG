package com.tfg.kanban.project.dto;

import com.tfg.kanban.domain.model.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Datos editables de un proyecto", example = """
        {"name":"Practica 2","description":"Modelo E/R y SQL","briefing":"...",
         "deadline":"2026-12-20","status":"EN_CURSO"}
        """)
public record UpdateProjectRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 150)
        String name,

        @Size(max = 1000)
        String description,

        String briefing,

        LocalDate deadline,

        @Schema(description = "Si se omite, se conserva el estado actual")
        ProjectStatus status
) {
}
