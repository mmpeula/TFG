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
        {"title":"Implementar el endpoint de login con JWT",
         "description":"Endpoint POST /api/auth/login que devuelve un token firmado",
         "reason":"El apartado 3 pide autenticacion con JWT","type":"TAREA","epic":"Autenticacion",
         "sprint":1,"priority":"ALTA","estimatedHours":6,"dueDate":"2026-10-14",
         "assigneeIds":[2],"assigneeNames":["Luis Perez"],"dependsOn":["Disenar el modelo de usuario"]}
        """)
public record ProposedTask(

        @NotBlank
        @Size(max = 200)
        String title,

        @Schema(description = "Que hay que hacer, en concreto")
        @Size(max = 2000)
        String description,

        @Schema(description = "Por que existe la tarea: cita la parte del documento de la que sale")
        @Size(max = 500)
        String reason,

        @Schema(description = "HISTORIA si es funcionalidad visible, TAREA si es tecnica, BUG si corrige algo")
        TaskType type,

        @Schema(description = "Bloque de trabajo al que pertenece")
        @Size(max = 100)
        String epic,

        @Schema(description = "Numero de sprint, empezando en 1")
        Integer sprint,

        TaskPriority priority,

        BigDecimal estimatedHours,

        @Schema(description = "Ultimo dia laborable de su sprint. Lo fija el servidor, no el modelo")
        LocalDate dueDate,

        @Schema(description = "Miembros del equipo a los que se reparte la tarea. Normalmente uno; "
                + "mas de uno solo cuando la IA ha decidido que la colaboracion aporta algo real")
        List<Long> assigneeIds,

        @Schema(description = "Nombres de los miembros, para poder mostrarlos sin otra consulta")
        List<String> assigneeNames,

        @Schema(description = "Titulos de las tareas de las que depende. Ya validadas: apuntan a una tarea real "
                + "en su sprint o en uno anterior")
        List<String> dependsOn
) {

    /** Nunca null, para no tener que comprobarlo en cada sitio que la recorre. */
    public List<String> dependsOnOrEmpty() {
        return dependsOn == null ? List.of() : dependsOn;
    }

    public List<Long> assigneeIdsOrEmpty() {
        return assigneeIds == null ? List.of() : assigneeIds;
    }

    public List<String> assigneeNamesOrEmpty() {
        return assigneeNames == null ? List.of() : assigneeNames;
    }
}
