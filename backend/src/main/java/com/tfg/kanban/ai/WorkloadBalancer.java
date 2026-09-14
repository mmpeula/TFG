package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.MemberWorkload;
import com.tfg.kanban.ai.dto.ProposedTask;
import com.tfg.kanban.domain.model.TeamMember;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/**
 * Reparte las tareas propuestas entre las personas del equipo.
 *
 * Al modelo se le pide que asigne cada tarea, pero un modelo pequeno reparte mal:
 * repite a la misma persona, se inventa nombres o deja tareas sin asignar. Por eso
 * aqui su sugerencia solo se acepta si el nombre casa con alguien real, y todo lo
 * demas se coloca en quien menos horas acumula. El resultado es determinista y
 * siempre queda equilibrado.
 */
@Component
public class WorkloadBalancer {

    /** Horas que se asumen cuando la tarea no trae estimacion. */
    private static final BigDecimal HORAS_POR_DEFECTO = BigDecimal.valueOf(3);

    public record Result(List<ProposedTask> tasks, List<MemberWorkload> workload) {
    }

    /**
     * @param proposed  tareas tal y como salieron del modelo
     * @param members   equipo entre el que repartir
     * @param people    cuantas personas usar (si es menor que el equipo, se usan las primeras)
     */
    public Result distribute(List<ProposedTask> proposed, List<TeamMember> members, Integer people) {
        if (members.isEmpty()) {
            return new Result(proposed, List.of());
        }

        List<TeamMember> target = members;
        if (people != null && people > 0 && people < members.size()) {
            target = members.subList(0, people);
        }

        Map<Long, BigDecimal> hours = new LinkedHashMap<>();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (TeamMember member : target) {
            Long id = member.getUser().getId();
            hours.put(id, BigDecimal.ZERO);
            counts.put(id, 0);
            names.put(id, member.getUser().getFullName());
        }

        List<Long> owners = new ArrayList<>();
        for (ProposedTask task : proposed) {
            BigDecimal taskHours = hoursOf(task);
            Long memberId = chooseOwner(task, target, hours);

            hours.merge(memberId, taskHours, BigDecimal::add);
            counts.merge(memberId, 1, Integer::sum);
            owners.add(memberId);
        }

        rebalanceEmptyMembers(proposed, owners, hours, counts);

        List<ProposedTask> assigned = new ArrayList<>();
        for (int index = 0; index < proposed.size(); index++) {
            Long owner = owners.get(index);
            assigned.add(proposed.get(index).assignedTo(owner, names.get(owner)));
        }

        List<MemberWorkload> workload = names.entrySet().stream()
                .map(entry -> new MemberWorkload(entry.getKey(), entry.getValue(),
                        counts.get(entry.getKey()), hours.get(entry.getKey())))
                .toList();

        return new Result(assigned, workload);
    }

    /**
     * Se respeta la sugerencia del modelo solo si nombra a alguien del equipo y
     * ademas no desequilibra: si esa persona ya carga mas que la que menos tiene,
     * la tarea va a la mas descargada. El modelo sugiere; el reparto lo decide esto.
     */
    private Long chooseOwner(ProposedTask task, List<TeamMember> members,
                             Map<Long, BigDecimal> hours) {
        Long leastLoaded = leastLoaded(hours);
        return matchSuggested(task, members)
                .filter(suggested -> hours.get(suggested).compareTo(hours.get(leastLoaded)) <= 0)
                .orElse(leastLoaded);
    }

    /**
     * Si sobran tareas para que todos tengan al menos una, nadie se queda a cero:
     * se le pasa la tarea mas pequena de quien mas acumula.
     */
    private void rebalanceEmptyMembers(List<ProposedTask> proposed, List<Long> owners,
                                       Map<Long, BigDecimal> hours, Map<Long, Integer> counts) {
        if (proposed.size() < counts.size()) return;

        for (Long empty : List.copyOf(counts.keySet())) {
            if (counts.get(empty) > 0) continue;

            Long donor = counts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (donor == null) return;

            int index = smallestTaskOf(proposed, owners, donor);
            if (index < 0) return;

            BigDecimal moved = hoursOf(proposed.get(index));
            owners.set(index, empty);
            counts.merge(donor, -1, Integer::sum);
            counts.merge(empty, 1, Integer::sum);
            hours.merge(donor, moved.negate(), BigDecimal::add);
            hours.merge(empty, moved, BigDecimal::add);
        }
    }

    private int smallestTaskOf(List<ProposedTask> proposed, List<Long> owners, Long owner) {
        int best = -1;
        BigDecimal bestHours = null;
        for (int index = 0; index < proposed.size(); index++) {
            if (!owner.equals(owners.get(index))) continue;
            BigDecimal current = hoursOf(proposed.get(index));
            if (bestHours == null || current.compareTo(bestHours) < 0) {
                bestHours = current;
                best = index;
            }
        }
        return best;
    }

    private BigDecimal hoursOf(ProposedTask task) {
        return task.estimatedHours() == null ? HORAS_POR_DEFECTO : task.estimatedHours();
    }

    /** Solo se respeta la sugerencia del modelo si el nombre corresponde a alguien del equipo. */
    private Optional<Long> matchSuggested(ProposedTask task, List<TeamMember> members) {
        return matchName(task.assigneeName(), members);
    }

    /**
     * Busca en la lista a quien mejor casa el nombre, normalizado (sin tildes, sin
     * mayusculas): nombre completo exacto, solo el nombre de pila, o que empiece
     * por el texto dado. Publico porque tambien lo usa {@link AiPlanService} para
     * resolver a quien excluir del reparto cuando el usuario lo pide.
     */
    public static Optional<Long> matchName(String name, List<TeamMember> members) {
        String normalized = normalize(name);
        if (normalized.isBlank()) return Optional.empty();

        return members.stream()
                .filter(member -> {
                    String full = normalize(member.getUser().getFullName());
                    String first = full.split(" ")[0];
                    return full.equals(normalized) || normalized.equals(first) || full.startsWith(normalized);
                })
                .map(member -> member.getUser().getId())
                .findFirst();
    }

    private Long leastLoaded(Map<Long, BigDecimal> hours) {
        return hours.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
