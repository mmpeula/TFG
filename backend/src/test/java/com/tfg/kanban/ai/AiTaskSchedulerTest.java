package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.ProposedTask;
import com.tfg.kanban.domain.model.TaskPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El modelo no elige fechas, las calcula este servicio: aqui se comprueba que las
 * tareas caen siempre al final de un sprint, que cada persona recorre todos los
 * sprints en vez de amontonar su trabajo contra la entrega, y que una entrega o
 * un inicio ya pasados no cuelan.
 */
class AiTaskSchedulerTest {

    private final AiTaskScheduler scheduler = new AiTaskScheduler();

    private ProposedTask task(String title, Long assigneeId) {
        return new ProposedTask(title, null, null, TaskPriority.MEDIA, null, null, assigneeId,
                null, null, java.util.List.of());
    }

    @Test
    @DisplayName("Una lista vacia se devuelve tal cual")
    void emptyListStaysEmpty() {
        assertThat(scheduler.schedule(List.of(), null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("resolveWindow calcula la misma ventana de fechas que usa schedule internamente")
    void resolveWindowMatchesScheduleWindow() {
        LocalDate start = LocalDate.now().plusDays(3);
        LocalDate deadline = LocalDate.now().plusDays(23);

        var window = scheduler.resolveWindow(start, deadline);

        assertThat(window.start()).isEqualTo(start);
        assertThat(window.end()).isEqualTo(deadline);
    }

    @Test
    @DisplayName("resolveWindow tambien aplica la ventana por defecto sin fechas del workspace")
    void resolveWindowDefaultsWithoutDates() {
        var window = scheduler.resolveWindow(null, null);

        assertThat(window.start()).isEqualTo(LocalDate.now());
        assertThat(window.end()).isEqualTo(LocalDate.now().plusDays(42));
    }

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

    // ------------------------------------------------------------ reparto

    @Test
    @DisplayName("Las tareas de una persona van de la primera iteracion a la ultima, no todas al final")
    void spreadsSamePersonTasksAcrossSprints() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate deadline = start.plusDays(62);

        var result = scheduler.schedule(
                List.of(task("Analisis", 1L), task("Implementacion", 1L), task("Pruebas", 1L)),
                start, deadline, 21);

        var sprints = scheduler.sprintsOf(scheduler.resolveWindow(start, deadline), 21);
        assertThat(sprints).hasSize(3);
        assertThat(result).extracting(ProposedTask::dueDate).containsExactly(
                sprints.get(0).end(), sprints.get(1).end(), sprints.get(2).end());
    }

    @Test
    @DisplayName("Cada tarea cae al final de un sprint, nunca en una fecha suelta entre medias")
    void everyTaskLandsOnASprintBoundary() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate deadline = start.plusDays(62);
        var tasks = List.of(task("A", 1L), task("B", 1L), task("C", 2L), task("D", 2L), task("E", null));

        var result = scheduler.schedule(tasks, start, deadline, 21);

        var finales = scheduler.sprintsOf(scheduler.resolveWindow(start, deadline), 21).stream()
                .map(AiTaskScheduler.Window::end).toList();
        assertThat(result).allSatisfy(task -> assertThat(finales).contains(task.dueDate()));
    }

    @Test
    @DisplayName("Con una sola tarea, esa persona entrega en el ultimo sprint")
    void aSingleTaskLandsOnTheLastSprint() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate deadline = start.plusDays(62);

        var result = scheduler.schedule(List.of(task("Unica", 1L)), start, deadline, 21);

        assertThat(result).singleElement().extracting(ProposedTask::dueDate).isEqualTo(deadline);
    }

    @Test
    @DisplayName("Cada persona avanza por su cuenta: que una tenga el triple de tareas no aprieta a la otra")
    void eachPersonHasItsOwnPace() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate deadline = start.plusDays(62);

        var result = scheduler.schedule(List.of(
                task("Ana - unica", 1L),
                task("Luis - una", 2L), task("Luis - dos", 2L), task("Luis - tres", 2L)),
                start, deadline, 21);

        // Ana entrega lo suyo al final, igual que Luis su ultima tarea, pero las de
        // Luis empiezan en el primer sprint en vez de acumularse contra la entrega.
        assertThat(result.get(0).dueDate()).isEqualTo(deadline);
        assertThat(result.get(3).dueDate()).isEqualTo(deadline);
        assertThat(result.get(1).dueDate()).isBefore(result.get(2).dueDate());
        assertThat(result.get(2).dueDate()).isBefore(result.get(3).dueDate());
    }

    @Test
    @DisplayName("Las tareas sin asignar tambien se reparten, agrupadas entre si")
    void unassignedTasksAreSpreadAsAGroup() {
        var result = scheduler.schedule(List.of(task("Suelta 1", null), task("Suelta 2", null)), null, null, 14);

        assertThat(result).extracting(ProposedTask::dueDate).doesNotContainNull();
        assertThat(result.get(0).dueDate()).isBefore(result.get(1).dueDate());
        assertThat(result.get(1).dueDate()).isEqualTo(LocalDate.now().plusDays(42));
    }

    @Test
    @DisplayName("Una entrega ya pasada no se usa: se cae en la ventana por defecto")
    void pastDeadlineFallsBackToDefaultWindow() {
        var result = scheduler.schedule(List.of(task("Tarea", 1L)), null, LocalDate.now().minusDays(5), 14);

        assertThat(result).singleElement()
                .extracting(ProposedTask::dueDate).isEqualTo(LocalDate.now().plusDays(42));
    }

    @Test
    @DisplayName("Un inicio ya pasado no se usa: se arranca desde hoy")
    void pastStartDateFallsBackToToday() {
        LocalDate deadline = LocalDate.now().plusDays(20);

        var result = scheduler.schedule(List.of(task("Tarea", 1L)), LocalDate.now().minusDays(10), deadline, 14);

        assertThat(result).singleElement().extracting(ProposedTask::dueDate).isEqualTo(deadline);
    }

    @Test
    @DisplayName("El resto de campos de la propuesta viaja intacto, solo se toca la fecha")
    void onlyTheDueDateChanges() {
        var original = new ProposedTask("Modelar el esquema", "porque lo pide el guion",
                com.tfg.kanban.domain.model.TaskType.HISTORIA, TaskPriority.ALTA,
                java.math.BigDecimal.valueOf(6), null, 2L, "Luis Perez",
                "Backend y Java", java.util.List.of("Ana Garcia"));

        var result = scheduler.schedule(List.of(original), null, null, 14);

        assertThat(result).singleElement().satisfies(t -> {
            assertThat(t.title()).isEqualTo(original.title());
            assertThat(t.reason()).isEqualTo(original.reason());
            assertThat(t.type()).isEqualTo(original.type());
            assertThat(t.priority()).isEqualTo(original.priority());
            assertThat(t.estimatedHours()).isEqualByComparingTo(original.estimatedHours());
            assertThat(t.assigneeId()).isEqualTo(original.assigneeId());
            assertThat(t.assigneeName()).isEqualTo(original.assigneeName());
            assertThat(t.dueDate()).isNotNull();
        });
    }
}
