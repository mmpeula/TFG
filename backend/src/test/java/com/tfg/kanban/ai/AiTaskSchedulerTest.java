package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.ProposedTask;
import com.tfg.kanban.domain.model.TaskPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El modelo no elige fechas, las calcula este servicio: aqui se comprueba que cada
 * persona avanza por su propia ventana, que la ultima tarea de cada una cae en la
 * fecha de entrega y que una entrega ya pasada o un inicio ya pasado no cuelan.
 */
class AiTaskSchedulerTest {

    private final AiTaskScheduler scheduler = new AiTaskScheduler();

    private ProposedTask task(String title, Long assigneeId) {
        return new ProposedTask(title, null, null, TaskPriority.MEDIA, null, null, assigneeId, null);
    }

    @Test
    @DisplayName("Una lista vacia se devuelve tal cual")
    void emptyListStaysEmpty() {
        assertThat(scheduler.schedule(List.of(), null, null)).isEmpty();
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

    @Test
    @DisplayName("Sin fecha de entrega, la unica tarea cae al final de la ventana por defecto (42 dias)")
    void singleTaskWithoutDeadlineLandsOnDefaultWindowEnd() {
        var result = scheduler.schedule(List.of(task("Unica", 1L)), null, null);

        assertThat(result).singleElement()
                .extracting(ProposedTask::dueDate).isEqualTo(LocalDate.now().plusDays(42));
    }

    @Test
    @DisplayName("Varias tareas de la misma persona se reparten en orden y la ultima cae en la entrega")
    void spreadsSamePersonTasksInOrder() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate deadline = LocalDate.now().plusDays(35);

        var result = scheduler.schedule(
                List.of(task("Analisis", 1L), task("Implementacion", 1L), task("Pruebas", 1L)), start, deadline);

        assertThat(result).extracting(ProposedTask::dueDate).containsExactly(
                start.plusDays(10), start.plusDays(20), start.plusDays(30));
        // La ultima tarea de la persona cae exactamente en la fecha de entrega.
        assertThat(result.get(2).dueDate()).isEqualTo(deadline);
    }

    @Test
    @DisplayName("Cada persona avanza por su propia ventana: la carga de una no aprieta a la otra")
    void eachPersonHasItsOwnPace() {
        var result = scheduler.schedule(List.of(
                task("Ana - unica", 1L),
                task("Luis - una", 2L), task("Luis - dos", 2L), task("Luis - tres", 2L)), null, null);

        LocalDate end = LocalDate.now().plusDays(42);
        // El unico entregable de Ana y el ultimo de Luis comparten fecha de entrega, aunque
        // Luis tenga el triple de tareas: cada persona llega al final por su cuenta.
        assertThat(result.get(0).dueDate()).isEqualTo(end);
        assertThat(result.get(3).dueDate()).isEqualTo(end);
        assertThat(result.get(1).dueDate()).isBefore(result.get(2).dueDate());
        assertThat(result.get(2).dueDate()).isBefore(result.get(3).dueDate());
    }

    @Test
    @DisplayName("Las tareas sin asignar tambien se reparten, agrupadas entre si")
    void unassignedTasksAreSpreadAsAGroup() {
        var result = scheduler.schedule(List.of(task("Suelta 1", null), task("Suelta 2", null)), null, null);

        assertThat(result).extracting(ProposedTask::dueDate).doesNotContainNull();
        assertThat(result.get(0).dueDate()).isBefore(result.get(1).dueDate());
        assertThat(result.get(1).dueDate()).isEqualTo(LocalDate.now().plusDays(42));
    }

    @Test
    @DisplayName("Una entrega ya pasada no se usa: se cae en la ventana por defecto")
    void pastDeadlineFallsBackToDefaultWindow() {
        var result = scheduler.schedule(List.of(task("Tarea", 1L)), null, LocalDate.now().minusDays(5));

        assertThat(result).singleElement()
                .extracting(ProposedTask::dueDate).isEqualTo(LocalDate.now().plusDays(42));
    }

    @Test
    @DisplayName("Un inicio ya pasado no se usa: se arranca desde hoy")
    void pastStartDateFallsBackToToday() {
        LocalDate deadline = LocalDate.now().plusDays(20);

        var result = scheduler.schedule(List.of(task("Tarea", 1L)), LocalDate.now().minusDays(10), deadline);

        assertThat(result).singleElement().extracting(ProposedTask::dueDate).isEqualTo(deadline);
    }

    @Test
    @DisplayName("El resto de campos de la propuesta viaja intacto, solo se toca la fecha")
    void onlyTheDueDateChanges() {
        var original = new ProposedTask("Modelar el esquema", "porque lo pide el guion",
                com.tfg.kanban.domain.model.TaskType.HISTORIA, TaskPriority.ALTA,
                java.math.BigDecimal.valueOf(6), null, 2L, "Luis Perez");

        var result = scheduler.schedule(List.of(original), null, null);

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
