package com.tfg.kanban.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las fechas de un plan generado con IA no las pone el modelo: salen de aqui, con
 * la misma regla que usa la pestana Plan, para que las tareas caigan en los sprints
 * que luego se pintan.
 */
class AiTaskSchedulerTest {

    private final AiTaskScheduler scheduler = new AiTaskScheduler();

    // ------------------------------------------------------------ sprints

    @Test
    @DisplayName("La ventana se parte en sprints de la duracion pedida y el ultimo llega hasta la entrega")
    void splitsTheWindowIntoSprints() {
        var window = new AiTaskScheduler.Window(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 12, 16));

        var sprints = scheduler.sprintsOf(window, 21);

        assertThat(sprints).hasSize(3);
        assertThat(sprints.get(0).start()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(sprints.get(0).end()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(sprints.get(2).end()).isEqualTo(window.end());
    }

    @Test
    @DisplayName("Un ultimo trozo demasiado corto se suma al sprint anterior en vez de ser un sprint de dos dias")
    void aTinyLeftoverJoinsThePreviousSprint() {
        // 30 dias con sprints de 14: el resto son 2 dias, que no dan para una iteracion.
        var window = new AiTaskScheduler.Window(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30));

        var sprints = scheduler.sprintsOf(window, 14);

        assertThat(sprints).hasSize(2);
        assertThat(sprints.get(1).end()).isEqualTo(window.end());
    }

    @Test
    @DisplayName("Sin duracion de sprint se asumen dos semanas, como en la pantalla de Plan")
    void defaultsToTwoWeekSprints() {
        var window = new AiTaskScheduler.Window(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 11));

        assertThat(scheduler.sprintsOf(window, null)).hasSize(3);
    }

    @Test
    @DisplayName("Sin fecha de entrega se encadenan tantos sprints como se pidan, todos completos")
    void consecutiveSprintsFromTheStart() {
        LocalDate inicio = LocalDate.of(2026, 10, 5);

        var sprints = scheduler.consecutiveSprints(inicio, 3, 14);

        assertThat(sprints).hasSize(3);
        assertThat(sprints.get(0).start()).isEqualTo(inicio);
        assertThat(sprints.get(1).start()).isEqualTo(inicio.plusDays(14));
        assertThat(sprints.get(2).end()).isEqualTo(inicio.plusDays(41));
        assertThat(sprints).allSatisfy(sprint -> assertThat(sprint.days()).isEqualTo(14));
    }

    // ------------------------------------------------------------ fechas de las tareas

    @Test
    @DisplayName("Un sprint que acaba en domingo entrega el viernes anterior")
    void weekendEndsFallBackToFriday() {
        // Del lunes 5 al domingo 18 de octubre de 2026.
        var sprint = new AiTaskScheduler.Window(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18));

        LocalDate fecha = scheduler.lastWorkingDay(sprint);

        assertThat(fecha).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(fecha.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("Si el sprint acaba entre semana, esa es la fecha")
    void weekdayEndsStay() {
        var sprint = new AiTaskScheduler.Window(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14));

        assertThat(scheduler.lastWorkingDay(sprint)).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    @DisplayName("Un sprint que es solo un fin de semana no retrocede fuera de si mismo")
    void neverLeavesTheSprint() {
        var sprint = new AiTaskScheduler.Window(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 18));

        assertThat(scheduler.lastWorkingDay(sprint)).isEqualTo(LocalDate.of(2026, 10, 17));
    }
}
