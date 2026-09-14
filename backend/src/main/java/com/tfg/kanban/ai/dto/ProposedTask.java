package com.tfg.kanban.ai.dto;

import com.tfg.kanban.domain.model.TaskPriority;
import com.tfg.kanban.domain.model.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Tarea propuesta por el modelo. No existe en el tablero hasta que se acepta.",
        example = """
        {"title":"Modelar el esquema de la base de datos","reason":"El guion pide un modelo E/R antes de implementar",
         "type":"HISTORIA","priority":"ALTA","estimatedHours":6,"dueDate":"2026-11-20",
         "assigneeId":2,"assigneeName":"Luis Perez"}
        """)
public record ProposedTask(

        @NotBlank
        @Size(max = 200)
        String title,

        @Schema(description = "Por que el modelo propone esta tarea")
        @Size(max = 300)
        String reason,

        @Schema(description = "De que va la tarea; si el modelo no lo dice o se equivoca, TAREA")
        TaskType type,

        TaskPriority priority,

        BigDecimal estimatedHours,

        @Schema(description = """
                Para cuando, calculado por el servidor repartiendo el trabajo de cada persona a lo
                largo de la ventana del workspace: no lo decide el modelo, para que las tareas caigan
                en un sprint real del plan en vez de amontonarse todas «sin fecha».
                """)
        LocalDate dueDate,

        @Schema(description = "Miembro del equipo al que se reparte la tarea")
        Long assigneeId,

        @Schema(description = "Nombre del miembro, para poder mostrarlo sin otra consulta")
        String assigneeName
) {

    /** Copia de la tarea con el reparto ya resuelto. */
    public ProposedTask assignedTo(Long id, String name) {
        return new ProposedTask(title, reason, type, priority, estimatedHours, dueDate, id, name);
    }

    /** Copia de la tarea con la fecha ya calculada. */
    public ProposedTask withDueDate(LocalDate date) {
        return new ProposedTask(title, reason, type, priority, estimatedHours, date, assigneeId, assigneeName);
    }
}
