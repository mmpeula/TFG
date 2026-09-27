package com.tfg.kanban.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Plan propuesto por Groq: sprints, tareas repartidas y avisos",
        example = """
        {"status":"ok","projectId":5,"model":"openai/gpt-oss-120b","source":"documento: guion-practica2.pdf",
         "message":"18 tareas en 3 sprints, repartidas entre 3 personas",
         "durationWeeks":6,"sprints":[],"tasks":[],"workload":[],"warnings":[],
         "attempts":1,"generatedAt":"2026-09-23T18:40:00Z"}
        """)
public record AiPlanResponse(
        String status,
        Long projectId,
        String model,
        @Schema(description = "De donde ha salido el texto: el guion guardado, un documento o una descripcion")
        String source,
        String message,
        @Schema(description = "Duracion total del plan en semanas")
        Integer durationWeeks,
        @Schema(description = "Sprints del plan, con las fechas calculadas por el servidor")
        List<PlannedSprint> sprints,
        List<ProposedTask> tasks,
        @Schema(description = "Horas y tareas de cada persona, contadas sobre las tareas del plan")
        List<MemberWorkload> workload,
        @Schema(description = "Avisos del modelo y reglas que el plan sigue sin cumplir tras los reintentos")
        List<String> warnings,
        @Schema(description = "Llamadas al modelo que ha costado: 1 si el primer plan era valido")
        int attempts,
        Instant generatedAt
) {

    /**
     * @param duplicates tareas que el modelo propuso pero que ya estaban en el
     *                   tablero; se cuentan para poder explicar por que a veces la
     *                   propuesta sale corta.
     */
    public static AiPlanResponse of(Long projectId, String model, String source, Integer durationWeeks,
                                    List<PlannedSprint> sprints, List<ProposedTask> tasks,
                                    List<MemberWorkload> workload, List<String> warnings, int duplicates,
                                    int attempts) {
        return new AiPlanResponse("ok", projectId, model, source,
                message(tasks.size(), sprints.size(), workload.size(), duplicates),
                durationWeeks, sprints, tasks, workload, warnings, attempts, Instant.now());
    }

    private static String message(int propuestas, int sprints, int personas, int duplicadas) {
        if (propuestas == 0 && duplicadas > 0) {
            return "El modelo ha propuesto %d %s, pero %s trabajo que ya esta en el tablero. Anade o cierra tareas y vuelve a intentarlo."
                    .formatted(duplicadas, duplicadas == 1 ? "tarea" : "tareas",
                            duplicadas == 1 ? "repetia" : "repetian");
        }
        if (propuestas == 0) {
            return "El modelo no ha propuesto ninguna tarea; prueba con un documento mas detallado.";
        }
        String base = "%d %s en %d %s, repartidas entre %d %s"
                .formatted(propuestas, propuestas == 1 ? "tarea" : "tareas",
                        sprints, sprints == 1 ? "sprint" : "sprints",
                        personas, personas == 1 ? "persona" : "personas");
        return duplicadas == 0
                ? base
                : "%s (se han descartado %d que ya estaban en el tablero)".formatted(base, duplicadas);
    }
}
