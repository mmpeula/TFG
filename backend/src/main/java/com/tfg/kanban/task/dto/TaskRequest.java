package com.tfg.kanban.task.dto;

import com.tfg.kanban.domain.model.TaskPriority;
import com.tfg.kanban.domain.model.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Datos de una tarea", example = """
        {"title":"Redactar la memoria","description":"Apartados 1 a 3","type":"TAREA","priority":"ALTA",
         "dueDate":"2026-12-01","estimatedHours":6.5,"assigneeIds":[2]}
        """)
public record TaskRequest(

        @NotBlank(message = "el titulo es obligatorio")
        @Size(max = 200)
        String title,

        String description,

        @Schema(description = "Si se omite, TAREA")
        TaskType type,

        @Schema(description = "Si se omite, MEDIA")
        TaskPriority priority,

        LocalDate dueDate,

        @DecimalMin(value = "0.0", inclusive = false, message = "la estimacion debe ser mayor que 0")
        @Digits(integer = 4, fraction = 2)
        BigDecimal estimatedHours,

        @Schema(description = "Lo que de verdad ha costado, si quien la ha hecho lo ha apuntado. Opcional")
        @DecimalMin(value = "0.0", inclusive = false, message = "las horas reales deben ser mayores que 0")
        @Digits(integer = 4, fraction = 2)
        BigDecimal actualHours,

        @Schema(description = "Ids de los miembros asignados; vacio o null para dejarla sin asignar")
        List<Long> assigneeIds
) {
}
