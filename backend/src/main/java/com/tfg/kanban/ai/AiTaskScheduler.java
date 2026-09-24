package com.tfg.kanban.ai;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * El calendario de sprints de un plan generado con IA.
 *
 * <p>El modelo decide en que sprint va cada tarea, pero las fechas de los sprints
 * las pone esto, con la misma regla que usa la pestana Plan (ver
 * {@code lib/sprints.ts}). Si las pusiera el modelo por su cuenta, sus sprints no
 * coincidirian con los que se pintan y las tareas caerian entre dos.</p>
 */
@Component
public class AiTaskScheduler {

    /** La misma que asume la pantalla de Plan cuando el workspace no lo dice. */
    static final int DEFAULT_SPRINT_DAYS = 14;

    /** Un tramo de dias, ambos incluidos: la ventana entera o un sprint. */
    public record Window(LocalDate start, LocalDate end) {

        long days() {
            return ChronoUnit.DAYS.between(start, end) + 1;
        }
    }

    /**
     * Parte la ventana en sprints.
     *
     * <p>Copia deliberada de {@code buildPlan} en {@code lib/sprints.ts}: si las dos
     * reglas no coincidieran, las tareas caerian entre dos sprints de los que se
     * pintan. Incluye por eso sus dos detalles: un ultimo trozo demasiado corto se
     * suma al anterior en vez de ser un sprint de dos dias, y el ultimo sprint llega
     * siempre hasta el final de la ventana.</p>
     */
    List<Window> sprintsOf(Window window, Integer sprintDays) {
        int dias = daysOrDefault(sprintDays);
        long total = window.days();

        int cuantos = (int) Math.max(1, Math.ceil((double) total / dias));
        long sobra = total - (long) (cuantos - 1) * dias;
        if (cuantos > 1 && sobra <= dias / 2.0) {
            cuantos--;
        }

        List<Window> sprints = new ArrayList<>(cuantos);
        for (int i = 0; i < cuantos; i++) {
            LocalDate inicio = window.start().plusDays((long) i * dias);
            LocalDate fin = i == cuantos - 1 ? window.end() : inicio.plusDays(dias - 1L);
            sprints.add(new Window(inicio, fin));
        }
        return sprints;
    }

    /**
     * Tantos sprints seguidos como se pidan, empezando el dia dado. Es el caso sin
     * fecha de entrega: la duracion la propone el modelo y aqui solo se le ponen
     * fechas.
     */
    List<Window> consecutiveSprints(LocalDate start, int count, Integer sprintDays) {
        int dias = daysOrDefault(sprintDays);
        return sprintsOf(new Window(start, start.plusDays((long) count * dias - 1)), dias);
    }

    /**
     * Ultimo dia laborable del sprint: es la fecha de todas sus tareas. Si el sprint
     * acaba en fin de semana se retrocede al viernes, porque nadie entrega en sabado.
     */
    LocalDate lastWorkingDay(Window sprint) {
        LocalDate dia = sprint.end();
        while (esFinDeSemana(dia) && dia.isAfter(sprint.start())) {
            dia = dia.minusDays(1);
        }
        return dia;
    }

    static int daysOrDefault(Integer sprintDays) {
        return sprintDays == null || sprintDays < 1 ? DEFAULT_SPRINT_DAYS : sprintDays;
    }

    private static boolean esFinDeSemana(LocalDate dia) {
        return dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
