package com.tfg.kanban.workspace.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Datos editables de un espacio de trabajo", example = """
        {"name":"Bases de Datos","description":"Practica 2","color":"oliva","icon":"BD",
         "startDate":"2026-10-01","deadline":"2026-12-15","sprintDays":14}
        """)
public record UpdateWorkspaceRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 150)
        String name,

        @Size(max = 1000)
        String description,

        @Size(max = 20)
        String color,

        @Size(max = 8)
        String icon,

        @Schema(description = "Inicio del trabajo, opcional")
        LocalDate startDate,

        @Schema(description = "Fecha de entrega, opcional")
        LocalDate deadline,

        @Schema(description = "Duracion del sprint en dias, igual para todo el plan; si se omite, 14")
        @Min(value = 7, message = "un sprint dura al menos una semana")
        @Max(value = 28, message = "un sprint dura como mucho cuatro semanas")
        Integer sprintDays
) {

    /** La entrega no puede ser antes del inicio: el plan se quedaria sin dias en los que repartir. */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "la fecha de entrega no puede ser anterior a la de inicio")
    public boolean isFechasEnOrden() {
        return startDate == null || deadline == null || !deadline.isBefore(startDate);
    }
}
