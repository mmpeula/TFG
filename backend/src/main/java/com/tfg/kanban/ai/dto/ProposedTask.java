package com.tfg.kanban.ai.dto;

import com.tfg.kanban.domain.model.TaskPriority;
import com.tfg.kanban.domain.model.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        String assigneeName,

        /**
         * Por que le ha tocado a esa persona, en una frase.
         *
         * <p>Lo escribe el servidor, no el modelo, porque el reparto final tampoco
         * lo decide el modelo: este sugiere y {@code WorkloadBalancer} confirma o
         * cambia la sugerencia segun la carga. Dejar aqui la frase del modelo
         * cuando el servidor le ha llevado la contraria seria guardar una
         * explicacion que no corresponde a lo que acabo pasando.</p>
         */
        @Schema(description = "Por que se le ha asignado a esa persona. Lo compone el servidor "
                + "combinando lo que dijo el modelo con lo que decidio el reparto por carga.",
                example = "Backend y Java (segun el modelo)")
        @Size(max = 300)
        String assignmentReason,

        /**
         * Quienes no pueden acabar con esta tarea, segun el modelo.
         *
         * <p>Es como llegan al reparto las prohibiciones escritas en la ficha de
         * cada persona: el equilibrado es determinista y no entiende «nunca le des
         * frontend», asi que el modelo traduce esas frases a nombres concretos por
         * tarea y el servidor los respeta. Vacia mientras nadie prohiba nada, que
         * es el caso corriente.</p>
         */
        @Schema(description = "Nombres que el reparto no debe elegir para esta tarea")
        List<String> avoid
) {

    /** Copia de la tarea con el reparto ya resuelto y su explicacion. */
    public ProposedTask assignedTo(Long id, String name, String why) {
        return new ProposedTask(title, reason, type, priority, estimatedHours, dueDate, id, name, why, avoid);
    }

    /** Copia de la tarea con la fecha ya calculada. */
    public ProposedTask withDueDate(LocalDate date) {
        return new ProposedTask(title, reason, type, priority, estimatedHours, date,
                assigneeId, assigneeName, assignmentReason, avoid);
    }

    /** Nunca null, para no tener que comprobarlo en cada sitio que la recorre. */
    public List<String> avoidOrEmpty() {
        return avoid == null ? List.of() : avoid;
    }
}
