package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.ProposedTask;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprueba en codigo lo que el prompt le pide al modelo y se puede medir.
 *
 * <p>Un modelo pequeno lee "nadie supera su capacidad" y aun asi mete 30 horas a
 * alguien que tiene 20. Por eso las reglas medibles no se dejan a su palabra: se
 * cuentan aqui, y cada fallo sale como una frase concreta —quien, que sprint,
 * cuanto sobra— que se le puede devolver tal cual para que corrija el plan.</p>
 *
 * <p>Lo que no se puede medir no se comprueba. Una preferencia o una restriccion
 * escritas en lenguaje natural ("no puede hacer frontend") solo las aplica el
 * modelo; lo que si se garantiza aqui es la capacidad, que incluye el caso de
 * alguien con 0 horas (no disponible en todo el plan).</p>
 */
final class PlanValidator {

    /** Mas errores no ayudan a corregir: un modelo pequeno se pierde en la lista. */
    private static final int MAX_ERRORS = 15;

    private PlanValidator() {
    }

    /**
     * @param tasks   tareas tal y como las devolvio el modelo, con su sprint y su
     *                persona por nombre
     * @param team    equipo del reparto
     * @param sprints sprints del plan, en orden; el numero de sprint es su posicion + 1
     * @return los errores encontrados; vacia si el plan es valido
     */
    static List<String> validate(List<ProposedTask> tasks, PlanTeam team,
                                 List<AiTaskScheduler.Window> sprints) {
        List<String> errors = new ArrayList<>();
        if (tasks.isEmpty()) {
            errors.add("El plan no tiene ninguna tarea.");
            return errors;
        }

        int numSprints = sprints.size();
        // horas[persona][sprint] y tareas por persona y sprint, solo de tareas validas
        Map<Long, Map<Integer, BigDecimal>> horas = new HashMap<>();
        Map<Long, Map<Integer, Integer>> cuantas = new HashMap<>();

        for (ProposedTask task : tasks) {
            var persona = team.match(task.assigneeName());
            if (persona.isEmpty()) {
                errors.add("La tarea «%s» esta asignada a «%s», que no esta en el equipo. Usa uno de estos nombres exactos: %s."
                        .formatted(task.title(), task.assigneeName() == null ? "nadie" : task.assigneeName(),
                                team.names()));
            }
            boolean sprintValido = task.sprint() != null && task.sprint() >= 1 && task.sprint() <= numSprints;
            if (!sprintValido) {
                errors.add("La tarea «%s» esta en el sprint %s, pero el plan tiene los sprints 1 a %d."
                        .formatted(task.title(), task.sprint(), numSprints));
            }
            if (task.estimatedHours() == null || task.estimatedHours().signum() <= 0) {
                errors.add("La tarea «%s» no tiene horas: estimalas entre 2 y 8.".formatted(task.title()));
            }
            if (persona.isPresent() && sprintValido && task.estimatedHours() != null) {
                Long id = persona.get().userId();
                horas.computeIfAbsent(id, k -> new HashMap<>())
                        .merge(task.sprint(), task.estimatedHours(), BigDecimal::add);
                cuantas.computeIfAbsent(id, k -> new HashMap<>())
                        .merge(task.sprint(), 1, Integer::sum);
            }
        }

        for (PlanTeam.Member member : team.members()) {
            for (int numero = 1; numero <= numSprints; numero++) {
                AiTaskScheduler.Window sprint = sprints.get(numero - 1);
                BigDecimal capacidad = member.capacityFor(sprint.days());
                BigDecimal suyas = horas.getOrDefault(member.userId(), Map.of())
                        .getOrDefault(numero, BigDecimal.ZERO);

                if (suyas.compareTo(capacidad) > 0) {
                    errors.add("%s tiene %s h en el sprint %d y su capacidad es %s h: quita o mueve %s h."
                            .formatted(member.name(), plano(suyas), numero, plano(capacidad),
                                    plano(suyas.subtract(capacidad))));
                }
                // Quien no tiene horas no puede tener trabajo: no se le exige tarea.
                if (member.hoursPerWeek() > 0
                        && cuantas.getOrDefault(member.userId(), Map.of()).getOrDefault(numero, 0) == 0) {
                    errors.add("%s no tiene ninguna tarea en el sprint %d: dale al menos una, aunque sea de "
                            .formatted(member.name(), numero)
                            + "apoyo (pruebas, documentacion o preparacion).");
                }
            }
        }

        errors.addAll(dependencyErrors(tasks));

        if (errors.size() > MAX_ERRORS) {
            int resto = errors.size() - MAX_ERRORS;
            List<String> recortados = new ArrayList<>(errors.subList(0, MAX_ERRORS));
            recortados.add("Y %d %s mas del mismo tipo.".formatted(resto, resto == 1 ? "error" : "errores"));
            return recortados;
        }
        return errors;
    }

    /** Ninguna tarea puede depender de otra que va en un sprint posterior. */
    private static List<String> dependencyErrors(List<ProposedTask> tasks) {
        Map<String, ProposedTask> porTitulo = new LinkedHashMap<>();
        for (ProposedTask task : tasks) {
            porTitulo.putIfAbsent(TextNormalizer.plain(task.title()), task);
        }

        List<String> errors = new ArrayList<>();
        for (ProposedTask task : tasks) {
            if (task.sprint() == null) continue;
            for (String titulo : task.dependsOnOrEmpty()) {
                ProposedTask previa = porTitulo.get(TextNormalizer.plain(titulo));
                // Una dependencia que no casa con ninguna tarea del plan no se
                // puede comprobar: se ignora en vez de inventar un error.
                if (previa == null || previa.sprint() == null) continue;
                if (previa.sprint() > task.sprint()) {
                    errors.add("La tarea «%s» (sprint %d) depende de «%s», que va en el sprint %d: mueve una de las dos."
                            .formatted(task.title(), task.sprint(), previa.title(), previa.sprint()));
                }
            }
        }
        return errors;
    }

    private static String plano(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
