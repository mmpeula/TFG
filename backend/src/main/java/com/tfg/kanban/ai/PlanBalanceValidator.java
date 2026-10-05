package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.ProposedTask;
import com.tfg.kanban.domain.model.TaskPriority;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Comprobaciones deterministas del reparto que el prompt le pide al modelo pero
 * que un modelo pequeno (deepseek-flash) no cumple siempre: horas, tareas
 * criticas por persona y quien implementa frente a quien valida.
 *
 * <p>Se ejecuta sobre lo que ha devuelto el modelo, resolviendo cada
 * "responsable" contra el equipo real con {@link PlanTeam#matchAll}, igual que
 * hace {@code AiPlanService} despues. Nada de esto sustituye al prompt: es la
 * red que hace falta porque un modelo pequeno, en la practica, cambia de quien
 * concentra el trabajo pero no deja de concentrarlo.</p>
 */
final class PlanBalanceValidator {

    /**
     * Diferencia admitida entre quien mas horas lleva y quien menos, sobre el
     * total de cada uno. No hay horas por semana que lo maticen: se asume que
     * todo el mundo parte de la misma disponibilidad salvo que sus preferencias
     * digan lo contrario, y eso solo lo puede leer el modelo, no esta comprobacion.
     */
    static final BigDecimal MAX_HOURS_GAP = new BigDecimal("0.15");

    /** Palabras que delatan una tarea de verificacion, no de implementacion. */
    private static final List<String> VALIDATION_WORDS =
            List.of("prueba", "test", "validar", "revisar", "revision", "verificar");

    private PlanBalanceValidator() {
    }

    record Violation(String code, String message) {
    }

    /**
     * Todo lo que incumple, o vacia si el reparto esta bien. Con menos de dos
     * personas en el reparto no hay nada que equilibrar.
     */
    static List<Violation> check(List<ProposedTask> tasks, PlanTeam team) {
        List<PlanTeam.Member> miembros = team.members();
        if (miembros.size() < 2 || tasks.isEmpty()) return List.of();

        List<Violation> violaciones = new ArrayList<>();
        violaciones.addAll(hoursGapViolation(hoursByMember(tasks, team), miembros));
        violaciones.addAll(criticalShareViolations(criticalCountByMember(tasks, team), miembros));
        violaciones.addAll(selfValidationViolations(tasks, team));
        violaciones.addAll(sprintCoverageViolations(tasks, team, miembros));
        return violaciones;
    }

    /** Horas asignadas a cada persona, sumando el total de la tarea a cada responsable. */
    static Map<PlanTeam.Member, BigDecimal> hoursByMember(List<ProposedTask> tasks, PlanTeam team) {
        Map<PlanTeam.Member, BigDecimal> horas = new LinkedHashMap<>();
        for (PlanTeam.Member member : team.members()) {
            horas.put(member, BigDecimal.ZERO);
        }
        for (ProposedTask task : tasks) {
            if (task.estimatedHours() == null) continue;
            for (PlanTeam.Member member : team.matchAll(task.assigneeNamesOrEmpty())) {
                horas.computeIfPresent(member, (m, actual) -> actual.add(task.estimatedHours()));
            }
        }
        return horas;
    }

    /** Tareas de prioridad CRITICA de cada persona. */
    static Map<PlanTeam.Member, Long> criticalCountByMember(List<ProposedTask> tasks, PlanTeam team) {
        Map<PlanTeam.Member, Long> criticas = new LinkedHashMap<>();
        for (PlanTeam.Member member : team.members()) {
            criticas.put(member, 0L);
        }
        for (ProposedTask task : tasks) {
            if (task.priority() != TaskPriority.CRITICA) continue;
            for (PlanTeam.Member member : team.matchAll(task.assigneeNamesOrEmpty())) {
                criticas.computeIfPresent(member, (m, actual) -> actual + 1);
            }
        }
        return criticas;
    }

    /** La diferencia entre quien mas horas lleva y quien menos, sobre el total de cada uno. */
    static BigDecimal hoursGap(Map<PlanTeam.Member, BigDecimal> horas) {
        BigDecimal max = horas.values().stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal min = horas.values().stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        return max.signum() == 0 ? BigDecimal.ZERO : max.subtract(min).divide(max, 4, RoundingMode.HALF_UP);
    }

    private static List<Violation> hoursGapViolation(Map<PlanTeam.Member, BigDecimal> horas,
                                                      List<PlanTeam.Member> miembros) {
        if (miembros.size() < 2) return List.of();
        BigDecimal gap = hoursGap(horas);
        if (gap.compareTo(MAX_HOURS_GAP) <= 0) return List.of();

        PlanTeam.Member masCargado = horas.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElseThrow();
        PlanTeam.Member menosCargado = horas.entrySet().stream().min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElseThrow();
        return List.of(new Violation("HOURS_GAP",
                ("Diferencia de horas del %d %% (máximo %d %%): %s lleva %s h y %s lleva %s h. Si alguno tiene "
                        + "menos tiempo esta vez, debería decirlo en sus preferencias.")
                        .formatted(percent(gap), percent(MAX_HOURS_GAP), masCargado.name(),
                                plano(horas.get(masCargado)), menosCargado.name(),
                                plano(horas.get(menosCargado)))));
    }

    private static List<Violation> criticalShareViolations(Map<PlanTeam.Member, Long> criticas,
                                                            List<PlanTeam.Member> miembros) {
        long total = criticas.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0 || miembros.size() < 2) return List.of();
        long limite = limiteCriticas(total, miembros.size());

        List<Violation> violaciones = new ArrayList<>();
        for (Map.Entry<PlanTeam.Member, Long> entry : criticas.entrySet()) {
            if (entry.getValue() > limite) {
                violaciones.add(new Violation("CRITICAL_SHARE",
                        "%s tiene %d de %d tareas críticas (máximo %d): reparte alguna con otra persona."
                                .formatted(entry.getKey().name(), entry.getValue(), total, limite)));
            }
        }
        return violaciones;
    }

    /** El mismo tope que se le pide al modelo en el prompt: ceil(criticas / miembros) + 1. */
    static long limiteCriticas(long totalCriticas, int miembros) {
        return (long) Math.ceil((double) totalCriticas / miembros) + 1;
    }

    /**
     * Por cada epica, quien implementa y quien valida (por palabras clave en el
     * titulo). Si los dos grupos son exactamente los mismos y solo hay una
     * persona, nadie de fuera revisa esa parte.
     */
    private static List<Violation> selfValidationViolations(List<ProposedTask> tasks, PlanTeam team) {
        Map<String, Set<PlanTeam.Member>> implementadores = new LinkedHashMap<>();
        Map<String, Set<PlanTeam.Member>> validadores = new LinkedHashMap<>();
        for (ProposedTask task : tasks) {
            if (task.epic() == null || task.epic().isBlank()) continue;
            boolean esValidacion = esDeValidacion(task.title());
            Map<String, Set<PlanTeam.Member>> destino = esValidacion ? validadores : implementadores;
            destino.computeIfAbsent(task.epic(), k -> new LinkedHashSet<>()).addAll(team.matchAll(task.assigneeNamesOrEmpty()));
        }

        List<Violation> violaciones = new ArrayList<>();
        for (Map.Entry<String, Set<PlanTeam.Member>> entry : validadores.entrySet()) {
            Set<PlanTeam.Member> validadoresEpica = entry.getValue();
            Set<PlanTeam.Member> implementadoresEpica = implementadores.getOrDefault(entry.getKey(), Set.of());
            if (implementadoresEpica.size() < 2 || validadoresEpica.isEmpty()) continue;

            Set<PlanTeam.Member> ajenos = new HashSet<>(validadoresEpica);
            ajenos.removeAll(implementadoresEpica);
            // Solo si CADA validador de la epica tambien la implemento: nadie de fuera la revisa.
            if (ajenos.isEmpty() && implementadoresEpica.containsAll(validadoresEpica)) {
                violaciones.add(new Violation("SELF_VALIDATION",
                        ("En «%s», quien valida es también quien implementa: asigna esa prueba o revisión a alguien "
                                + "que no haya hecho esa parte.").formatted(entry.getKey())));
            }
        }
        return violaciones;
    }

    private static boolean esDeValidacion(String titulo) {
        String plano = TextNormalizer.plain(titulo);
        return VALIDATION_WORDS.stream().anyMatch(plano::contains);
    }

    /**
     * Por cada sprint con tareas criticas o altas suficientes para todos, quien
     * se queda sin ninguna. Si no hay ni una por persona, no es un problema del
     * reparto: no habia para repartir.
     */
    private static List<Violation> sprintCoverageViolations(List<ProposedTask> tasks, PlanTeam team,
                                                             List<PlanTeam.Member> miembros) {
        int totalSprints = tasks.stream().map(ProposedTask::sprint).filter(Objects::nonNull)
                .max(Integer::compare).orElse(0);
        List<Violation> violaciones = new ArrayList<>();

        for (int sprint = 1; sprint <= totalSprints; sprint++) {
            final int s = sprint;
            List<ProposedTask> delSprint = tasks.stream().filter(t -> Objects.equals(t.sprint(), s)).toList();
            long relevantes = delSprint.stream().filter(PlanBalanceValidator::esCriticaOAlta).count();
            if (relevantes < miembros.size()) continue;

            Set<PlanTeam.Member> conTarea = new HashSet<>();
            for (ProposedTask task : delSprint) {
                if (!esCriticaOAlta(task)) continue;
                conTarea.addAll(team.matchAll(task.assigneeNamesOrEmpty()));
            }
            for (PlanTeam.Member member : miembros) {
                if (!conTarea.contains(member)) {
                    violaciones.add(new Violation("SPRINT_COVERAGE",
                            "En el sprint %d, %s no tiene ninguna tarea crítica ni alta."
                                    .formatted(sprint, member.name())));
                }
            }
        }
        return violaciones;
    }

    private static boolean esCriticaOAlta(ProposedTask task) {
        return task.priority() == TaskPriority.CRITICA || task.priority() == TaskPriority.ALTA;
    }

    private static int percent(BigDecimal share) {
        return share.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static String plano(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
