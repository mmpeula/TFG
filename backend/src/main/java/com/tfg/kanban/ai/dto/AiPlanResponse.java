package com.tfg.kanban.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Planificacion propuesta por el modelo local, ya repartida entre el equipo",
        example = """
        {"status":"ok","projectId":5,"model":"llama3","source":"documento: guion-practica2.pdf",
         "message":"6 tareas propuestas y repartidas entre 4 personas",
         "tasks":[],"workload":[],
         "schedule":{"startDate":"2026-10-01","deadline":"2026-12-20","sprintDays":14},
         "excludedMembers":[],
         "generatedAt":"2026-08-31T18:40:00Z"}
        """)
public record AiPlanResponse(
        String status,
        Long projectId,
        String model,
        @Schema(description = "De donde ha salido el texto: el guion guardado o un documento subido")
        String source,
        String message,
        List<ProposedTask> tasks,
        @Schema(description = "Reparto resultante, una entrada por persona")
        List<MemberWorkload> workload,
        @Schema(description = "Fecha de inicio, entrega y duracion de sprint que el documento menciona "
                + "explicitamente. Null si el documento no dice nada fiable: no se inventan fechas.")
        SuggestedSchedule schedule,
        @Schema(description = "Personas del equipo dejadas fuera del reparto porque las indicaciones "
                + "del usuario lo pedian explicitamente. Vacia si no se ha excluido a nadie.")
        List<String> excludedMembers,
        Instant generatedAt
) {

    /**
     * Lo que el documento dice de fechas, tal cual, sin repartir ni calcular nada:
     * eso es cosa de {@link com.tfg.kanban.ai.AiTaskScheduler}. Cada campo es
     * independiente porque un enunciado real rara vez los da todos juntos (dice la
     * entrega pero no la duracion del sprint, o al reves).
     */
    public record SuggestedSchedule(LocalDate startDate, LocalDate deadline, Integer sprintDays) {

        // Sin @JsonIgnore, Jackson trata isEmpty() como si fuera el campo "empty":
        // es un metodo de ayuda interno para of(...), no parte de la respuesta.
        @JsonIgnore
        public boolean isEmpty() {
            return startDate == null && deadline == null && sprintDays == null;
        }
    }

    /**
     * @param duplicates      tareas que el modelo propuso pero que ya estaban en
     *                        el tablero. Se cuentan para poder explicar por que a
     *                        veces la propuesta sale corta o vacia.
     * @param schedule        fechas/duracion de sprint sugeridas por el documento,
     *                        o null si no ha detectado ninguna (se normaliza aqui:
     *                        un objeto con los tres campos a null tambien pasa a
     *                        null).
     * @param excludedMembers a quien se ha dejado fuera del reparto por indicacion
     *                        del usuario; ya resueltos contra el equipo real, no
     *                        los nombres tal cual los dio el modelo.
     */
    public static AiPlanResponse of(Long projectId, String model, String source,
                                    List<ProposedTask> tasks, List<MemberWorkload> workload, int duplicates,
                                    SuggestedSchedule schedule, List<String> excludedMembers) {
        return new AiPlanResponse("ok", projectId, model, source,
                message(tasks.size(), workload.size(), duplicates), tasks, workload,
                schedule == null || schedule.isEmpty() ? null : schedule, excludedMembers, Instant.now());
    }

    private static String message(int propuestas, int personas, int duplicadas) {
        if (propuestas == 0 && duplicadas > 0) {
            return "El modelo ha propuesto %d %s, pero %s trabajo que ya esta en el tablero. Anade o cierra tareas y vuelve a intentarlo."
                    .formatted(duplicadas, duplicadas == 1 ? "tarea" : "tareas",
                            duplicadas == 1 ? "repetia" : "repetian");
        }
        if (propuestas == 0) {
            return "El modelo no ha propuesto ninguna tarea; prueba con un documento mas detallado.";
        }
        String base = "%d %s %s entre %d %s"
                .formatted(propuestas, propuestas == 1 ? "tarea propuesta" : "tareas propuestas",
                        propuestas == 1 ? "y asignada" : "y repartidas", personas,
                        personas == 1 ? "persona" : "personas");
        return duplicadas == 0
                ? base
                : "%s (se han descartado %d que ya estaban en el tablero)".formatted(base, duplicadas);
    }
}
