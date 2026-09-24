package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.PlanOptions;
import com.tfg.kanban.ai.dto.ProposedTask;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.TeamMember;
import com.tfg.kanban.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las reglas que se comprueban en codigo, una a una. Cada error tiene que decir
 * quien, donde y cuanto, porque se le devuelve tal cual al modelo para que corrija.
 */
class PlanValidatorTest {

    private static final LocalDate INICIO = LocalDate.of(2027, 1, 4);

    /** Dos sprints de 14 dias: 20 h de capacidad por sprint con 10 h/semana. */
    private final List<AiTaskScheduler.Window> sprints = List.of(
            new AiTaskScheduler.Window(INICIO, INICIO.plusDays(13)),
            new AiTaskScheduler.Window(INICIO.plusDays(14), INICIO.plusDays(27)));

    private TeamMember member(long id, String name) {
        return TeamMember.builder().id(id).role(Role.MIEMBRO)
                .user(User.builder().id(id).fullName(name).build()).build();
    }

    private PlanTeam equipo(Integer horasLuis) {
        return PlanTeam.resolve(List.of(member(1, "Ana Garcia"), member(2, "Luis Perez")), List.of(
                new PlanOptions.TeamSlot(1L, 10, null),
                new PlanOptions.TeamSlot(2L, horasLuis, null)));
    }

    private ProposedTask task(String title, Integer sprint, String assignee, Integer hours, String... dependsOn) {
        return new ProposedTask(title, null, null, null, null, sprint, null,
                hours == null ? null : BigDecimal.valueOf(hours), null, null, assignee, List.of(dependsOn));
    }

    /** Una tarea por persona y sprint, todas dentro de capacidad. */
    private List<ProposedTask> planValido() {
        return new ArrayList<>(List.of(
                task("Modelo de datos", 1, "Ana Garcia", 6),
                task("API", 1, "Luis Perez", 8, "Modelo de datos"),
                task("Pantallas", 2, "Ana Garcia", 6),
                task("Pruebas", 2, "Luis Perez", 5)));
    }

    @Test
    @DisplayName("Un plan que cumple todo no tiene errores")
    void aValidPlanPasses() {
        assertThat(PlanValidator.validate(planValido(), equipo(10), sprints)).isEmpty();
    }

    @Test
    @DisplayName("Un plan sin tareas es un error")
    void anEmptyPlanFails() {
        assertThat(PlanValidator.validate(List.of(), equipo(10), sprints))
                .containsExactly("El plan no tiene ninguna tarea.");
    }

    @Test
    @DisplayName("Pasarse de capacidad en un sprint se dice con las horas exactas")
    void detectsOverCapacity() {
        List<ProposedTask> plan = planValido();
        plan.add(task("Otra API", 1, "Luis Perez", 8));
        plan.add(task("Y otra", 1, "Luis Perez", 8));

        assertThat(PlanValidator.validate(plan, equipo(10), sprints))
                .containsExactly("Luis Perez tiene 24 h en el sprint 1 y su capacidad es 20 h: quita o mueve 4 h.");
    }

    @Test
    @DisplayName("La capacidad sale de las horas por semana de cada persona")
    void capacityFollowsWeeklyHours() {
        // Con 6 h/semana, Luis solo tiene 12 h por sprint: las 8 de la API ya caben,
        // pero 8 + 5 en el sprint 2 no.
        List<ProposedTask> plan = planValido();
        plan.add(task("Documentar", 2, "Luis Perez", 8));

        assertThat(PlanValidator.validate(plan, equipo(6), sprints))
                .containsExactly("Luis Perez tiene 13 h en el sprint 2 y su capacidad es 12 h: quita o mueve 1 h.");
    }

    @Test
    @DisplayName("Nadie puede quedarse un sprint sin tareas")
    void everyoneWorksEverySprint() {
        List<ProposedTask> plan = planValido();
        plan.removeIf(task -> task.title().equals("Pruebas"));

        assertThat(PlanValidator.validate(plan, equipo(10), sprints))
                .singleElement()
                .satisfies(error -> assertThat(error).startsWith("Luis Perez no tiene ninguna tarea en el sprint 2"));
    }

    @Test
    @DisplayName("Quien tiene 0 horas no puede recibir trabajo, pero tampoco se le exige")
    void zeroHoursMeansUnavailable() {
        List<ProposedTask> plan = new ArrayList<>(List.of(
                task("Modelo de datos", 1, "Ana Garcia", 6),
                task("Pantallas", 2, "Ana Garcia", 6)));
        assertThat(PlanValidator.validate(plan, equipo(0), sprints)).isEmpty();

        plan.add(task("API", 1, "Luis Perez", 4));
        assertThat(PlanValidator.validate(plan, equipo(0), sprints))
                .containsExactly("Luis Perez tiene 4 h en el sprint 1 y su capacidad es 0 h: quita o mueve 4 h.");
    }

    @Test
    @DisplayName("Una persona que no esta en el equipo se nombra junto con los nombres validos")
    void unknownAssignee() {
        List<ProposedTask> plan = planValido();
        plan.add(task("Revisar", 1, "Pablo", 2));

        assertThat(PlanValidator.validate(plan, equipo(10), sprints))
                .containsExactly("La tarea «Revisar» esta asignada a «Pablo», que no esta en el equipo. "
                        + "Usa uno de estos nombres exactos: Ana Garcia, Luis Perez.");
    }

    @Test
    @DisplayName("Un sprint que no existe es un error")
    void sprintOutOfRange() {
        List<ProposedTask> plan = planValido();
        plan.add(task("Desplegar", 3, "Ana Garcia", 2));

        assertThat(PlanValidator.validate(plan, equipo(10), sprints))
                .containsExactly("La tarea «Desplegar» esta en el sprint 3, pero el plan tiene los sprints 1 a 2.");
    }

    @Test
    @DisplayName("Una tarea sin horas es un error: sin ellas no se puede medir la capacidad")
    void missingHours() {
        List<ProposedTask> plan = planValido();
        plan.add(task("Sin estimar", 1, "Ana Garcia", null));

        assertThat(PlanValidator.validate(plan, equipo(10), sprints))
                .containsExactly("La tarea «Sin estimar» no tiene horas: estimalas entre 2 y 8.");
    }

    @Test
    @DisplayName("Ninguna tarea puede depender de otra de un sprint posterior")
    void dependencyOnALaterSprint() {
        List<ProposedTask> plan = planValido();
        plan.set(1, task("API", 1, "Luis Perez", 8, "Pantallas"));

        assertThat(PlanValidator.validate(plan, equipo(10), sprints))
                .containsExactly("La tarea «API» (sprint 1) depende de «Pantallas», que va en el sprint 2: mueve una de las dos.");
    }

    @Test
    @DisplayName("Una dependencia que no casa con ninguna tarea se ignora en vez de inventar un error")
    void unknownDependencyIsIgnored() {
        List<ProposedTask> plan = planValido();
        plan.set(1, task("API", 1, "Luis Perez", 8, "Algo que no esta en el plan"));

        assertThat(PlanValidator.validate(plan, equipo(10), sprints)).isEmpty();
    }

    @Test
    @DisplayName("Con muchos errores se recortan: una lista enorme no ayuda a corregir")
    void errorsAreCapped() {
        List<ProposedTask> plan = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            plan.add(task("Tarea " + i, 9, "Nadie", null));
        }

        List<String> errors = PlanValidator.validate(plan, equipo(10), sprints);
        assertThat(errors).hasSize(16);
        assertThat(errors.get(15)).startsWith("Y ").endsWith("mas del mismo tipo.");
    }
}
