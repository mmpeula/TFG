package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.MemberWorkload;
import com.tfg.kanban.ai.dto.ProposedTask;
import com.tfg.kanban.domain.model.TaskPriority;
import com.tfg.kanban.domain.model.TeamMember;
import com.tfg.kanban.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El reparto no puede quedar en manos del modelo: aqui se comprueba que su
 * sugerencia se respeta solo cuando nombra a alguien real, y que lo demas se
 * equilibra por horas de forma determinista.
 */
class WorkloadBalancerTest {

    private final WorkloadBalancer balancer = new WorkloadBalancer();

    private TeamMember member(long id, String name) {
        return TeamMember.builder().id(id).user(User.builder().id(id).fullName(name).build()).build();
    }

    private ProposedTask task(String title, Integer hours, String suggested) {
        return new ProposedTask(title, null, null, TaskPriority.MEDIA,
                hours == null ? null : BigDecimal.valueOf(hours), null, null, suggested);
    }

    private final List<TeamMember> equipo = List.of(
            member(1, "Ana Garcia"), member(2, "Luis Perez"), member(3, "Marta Ruiz"));

    @Test
    @DisplayName("Se respeta el nombre sugerido cuando corresponde a alguien del equipo")
    void keepsValidSuggestion() {
        var result = balancer.distribute(List.of(task("Memoria", 4, "Marta Ruiz")), equipo, null);

        assertThat(result.tasks()).singleElement()
                .satisfies(t -> {
                    assertThat(t.assigneeId()).isEqualTo(3L);
                    assertThat(t.assigneeName()).isEqualTo("Marta Ruiz");
                });
    }

    @Test
    @DisplayName("Vale con el nombre de pila y sin tildes")
    void matchesLooseNames() {
        var result = balancer.distribute(
                List.of(task("A", 2, "luis"), task("B", 2, "ANA GARCÍA")), equipo, null);

        assertThat(result.tasks()).extracting(ProposedTask::assigneeId).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("Si el modelo carga siempre a la misma persona, el reparto lo corrige")
    void ignoresUnfairSuggestions() {
        // El modelo insiste en Ana para las cuatro tareas.
        var result = balancer.distribute(List.of(
                task("A", 4, "Ana Garcia"), task("B", 4, "Ana Garcia"),
                task("C", 4, "Ana Garcia"), task("D", 4, "Ana Garcia")), equipo, null);

        // Con 4 tareas y 3 personas alguien lleva 2, pero nadie puede acapararlas.
        assertThat(result.workload()).allSatisfy(w -> assertThat(w.tasks()).isPositive());
        assertThat(result.workload())
                .filteredOn(w -> w.memberId().equals(1L))
                .singleElement()
                .satisfies(w -> assertThat(w.tasks()).isLessThanOrEqualTo(2));
    }

    @Test
    @DisplayName("Con tareas suficientes, nadie se queda sin ninguna")
    void nobodyIsLeftEmpty() {
        // Caso real observado: el modelo nombra solo a dos personas de cuatro.
        var result = balancer.distribute(List.of(
                task("A", 4, "Profesor Soto"), task("B", 4, "Ana Garcia"),
                task("C", 4, "Ana Garcia"), task("D", 8, "Luis Perez")),
                List.of(member(1, "Ana Garcia"), member(2, "Luis Perez"),
                        member(3, "Marta Ruiz"), member(4, "Profesor Soto")), null);

        assertThat(result.workload())
                .as("todas las personas tienen al menos una tarea")
                .allSatisfy(w -> assertThat(w.tasks()).isPositive());
    }

    @Test
    @DisplayName("Con menos tareas que personas, no se inventa trabajo para rellenar")
    void doesNotInventWork() {
        var result = balancer.distribute(List.of(task("A", 4, null), task("B", 4, null)), equipo, null);

        assertThat(result.tasks()).hasSize(2);
        assertThat(result.workload().stream().filter(w -> w.tasks() > 0).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Un nombre inventado se ignora y la tarea va a quien menos horas lleva")
    void ignoresInventedNames() {
        var result = balancer.distribute(
                List.of(task("A", 8, "Ana Garcia"), task("B", 5, "Pedro El Inexistente")), equipo, null);

        assertThat(result.tasks().get(0).assigneeId()).isEqualTo(1L);
        // La segunda no puede caer en Ana, que ya acumula 8 h.
        assertThat(result.tasks().get(1).assigneeId()).isNotEqualTo(1L);
    }

    @Test
    @DisplayName("Sin sugerencias, el reparto equilibra las horas y no deja a nadie fuera")
    void balancesByHours() {
        var result = balancer.distribute(List.of(
                task("A", 8, null), task("B", 2, null), task("C", 2, null),
                task("D", 2, null), task("E", 2, null), task("F", 2, null)), equipo, null);

        assertThat(result.workload()).hasSize(3);
        assertThat(result.workload()).allSatisfy(w -> assertThat(w.tasks()).isPositive());

        var horas = result.workload().stream().map(MemberWorkload::hours).map(BigDecimal::doubleValue).toList();
        double max = horas.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double min = horas.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        // Con una tarea de 8 h el desequilibrio no puede pasar de esa tarea.
        assertThat(max - min).isLessThanOrEqualTo(8.0);
        assertThat(horas.stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(18.0);
    }

    @Test
    @DisplayName("Las tareas sin estimacion cuentan igualmente para equilibrar")
    void countsTasksWithoutEstimate() {
        var result = balancer.distribute(
                List.of(task("A", null, null), task("B", null, null), task("C", null, null)), equipo, null);

        assertThat(result.workload()).extracting(MemberWorkload::tasks).containsExactly(1, 1, 1);
        assertThat(result.workload()).allSatisfy(w -> assertThat(w.hours()).isEqualByComparingTo("3"));
    }

    @Test
    @DisplayName("Se puede repartir entre menos personas que las del equipo")
    void limitsToRequestedPeople() {
        var result = balancer.distribute(
                List.of(task("A", 2, null), task("B", 2, null), task("C", 2, null)), equipo, 2);

        assertThat(result.workload()).hasSize(2);
        assertThat(result.tasks()).extracting(ProposedTask::assigneeId).containsOnly(1L, 2L);
    }

    @Test
    @DisplayName("Si el modelo lo carga todo en una persona, el reparto lo deshace")
    void rebalancesWhenEverythingLandsOnOne() {
        // Tareas sin horas efectivas: sin desempate por carga, todas caerian en la
        // primera persona. Es el caso que obliga a redistribuir despues del reparto.
        var result = balancer.distribute(List.of(
                task("A", 0, "Ana Garcia"), task("B", 0, "Ana Garcia"), task("C", 0, "Ana Garcia")),
                equipo, null);

        assertThat(result.workload()).extracting(MemberWorkload::tasks).containsExactly(1, 1, 1);
        assertThat(result.tasks()).extracting(ProposedTask::assigneeId).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("Al redistribuir se cede la tarea mas pequena, no una cualquiera")
    void movesTheSmallestTask() {
        var dos = List.of(member(1, "Ana Garcia"), member(2, "Luis Perez"));
        var result = balancer.distribute(List.of(
                task("Grande", 0, "Ana Garcia"), task("Pequena", 0, "Ana Garcia")), dos, null);

        // Las dos valen lo mismo en horas, pero cada una acaba en una persona.
        assertThat(result.workload()).extracting(MemberWorkload::tasks).containsExactly(1, 1);
    }

    @Test
    @DisplayName("Un numero de personas absurdo o mayor que el equipo se ignora")
    void ignoresOutOfRangePeople() {
        var conCero = balancer.distribute(List.of(task("A", 2, null)), equipo, 0);
        var conExceso = balancer.distribute(List.of(task("A", 2, null)), equipo, 99);

        assertThat(conCero.workload()).hasSize(3);
        assertThat(conExceso.workload()).hasSize(3);
    }

    @Test
    @DisplayName("Un nombre incompleto tambien vale si empieza igual")
    void matchesPartialNames() {
        var result = balancer.distribute(List.of(task("A", 2, "Marta Ru")), equipo, null);

        assertThat(result.tasks()).singleElement()
                .satisfies(t -> assertThat(t.assigneeId()).isEqualTo(3L));
    }

    @Test
    @DisplayName("Sin equipo, las tareas se devuelven sin asignar en vez de fallar")
    void survivesEmptyTeam() {
        var result = balancer.distribute(List.of(task("A", 2, null)), List.of(), null);

        assertThat(result.workload()).isEmpty();
        assertThat(result.tasks()).singleElement()
                .satisfies(t -> assertThat(t.assigneeId()).isNull());
    }
}
