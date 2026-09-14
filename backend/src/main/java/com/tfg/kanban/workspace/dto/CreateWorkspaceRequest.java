package com.tfg.kanban.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de un workspace. Solo el nombre es obligatorio: es la condicion para que
 * crear el primero cueste segundos y no haya que entender el modelo de datos.
 */
@Schema(description = "Alta de un workspace", example = """
        {"name":"Bases de Datos","description":"Practica 2 - modelo E/R","color":"arena","icon":"BD",
         "sprintDays":14}
        """)
public record CreateWorkspaceRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 150)
        String name,

        @Size(max = 1000)
        String description,

        @Size(max = 20)
        String color,

        @Size(max = 8)
        String icon,

        @Schema(description = "Duracion del sprint en dias, igual para todo el plan; si se omite, 14")
        @Min(value = 1, message = "la duracion del sprint debe ser de al menos 1 dia")
        Integer sprintDays
) {
}
