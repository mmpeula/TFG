package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.MemberWorkload;
import com.tfg.kanban.ai.dto.ProposedTask;
import com.tfg.kanban.domain.model.TeamMember;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Reparte las tareas propuestas entre las personas del equipo.
 *
 * <p>Quien sabe de personas es el modelo: tiene delante la ficha de cada una y
 * puede leer que «se maneja con Java» encaja con «montar el endpoint de login».
 * Quien sabe de cuentas es esto: un modelo pequeno reparte mal —repite a la misma
 * persona, se inventa nombres, deja tareas sin asignar— y ademas no lleva la
 * cuenta de las horas que va acumulando cada uno.</p>
 *
 * <p>De ahi el reparto de papeles: el modelo sugiere y aqui se confirma o se
 * cambia, siempre de forma determinista. Su sugerencia se acepta si nombra a
 * alguien real, si esa tarea no lo tiene prohibido y si aceptarla no desequilibra
 * mas de lo que dura una tarea media; cuando no, la tarea va a quien menos carga
 * lleva de los que pueden hacerla. Cada asignacion sale con la frase que explica
 * cual de esos caminos se tomo.</p>
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

        BigDecimal tolerance = toleranceOf(proposed);
        List<Long> owners = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (ProposedTask task : proposed) {
            Decision decision = chooseOwner(task, target, hours, tolerance);

            hours.merge(decision.memberId(), hoursOf(task), BigDecimal::add);
            counts.merge(decision.memberId(), 1, Integer::sum);
            owners.add(decision.memberId());
            reasons.add(decision.why());
        }

        rebalanceEmptyMembers(proposed, owners, reasons, hours, counts, target);

        List<ProposedTask> assigned = new ArrayList<>();
        for (int index = 0; index < proposed.size(); index++) {
            Long owner = owners.get(index);
            assigned.add(proposed.get(index).assignedTo(owner, names.get(owner), reasons.get(index)));
        }

        List<MemberWorkload> workload = names.entrySet().stream()
                .map(entry -> new MemberWorkload(entry.getKey(), entry.getValue(),
                        counts.get(entry.getKey()), hours.get(entry.getKey())))
                .toList();

        return new Result(assigned, workload);
    }

    /**
     * A quien le toca la tarea y por que, en una frase.
     *
     * <p>El modelo sugiere mirando lo que sabe de cada persona; aqui se confirma o
     * se cambia mirando la carga. La regla es dejarle ganar salvo que la sugerencia
     * desequilibre de verdad: antes se exigia que la persona sugerida no llevara ni
     * una hora mas que la que menos tenia, y con eso la especializacion no
     * sobrevivia a la primera tarea —a la segunda de backend el especialista ya
     * cargaba mas que nadie y el reparto se la daba a otro—. Con un margen de una
     * tarea de holgura, el especialista puede encadenar lo suyo y el reparto solo
     * interviene cuando la diferencia empieza a ser injusta.</p>
     */
    private Decision chooseOwner(ProposedTask task, List<TeamMember> members,
                                 Map<Long, BigDecimal> hours, BigDecimal tolerance) {

        List<TeamMember> elegibles = eligibleFor(task, members);
        Long leastLoaded = leastLoaded(hours, elegibles);

        Optional<Long> sugerido = matchSuggested(task, elegibles);
        if (sugerido.isEmpty()) {
            // O el modelo no nombro a nadie, o nombro a alguien que no esta en el
            // equipo, o a alguien que esta prohibido para esta tarea.
            return new Decision(leastLoaded, motivoDeReparto(task, members, elegibles));
        }

        BigDecimal suya = hours.get(sugerido.get());
        boolean cabe = suya.subtract(hours.get(leastLoaded)).compareTo(tolerance) <= 0;
        if (cabe) {
            return new Decision(sugerido.get(), razonDelModelo(task));
        }
        return new Decision(leastLoaded,
                "Reparto: quien encajaba mejor ya acumula bastante mas trabajo");
    }

    /** A quien se le puede dar, quitando a los que el modelo marco como prohibidos. */
    private List<TeamMember> eligibleFor(ProposedTask task, List<TeamMember> members) {
        List<String> avoid = task.avoidOrEmpty();
        if (avoid.isEmpty()) {
            return members;
        }
        List<TeamMember> elegibles = new ArrayList<>(members);
        for (String name : avoid) {
            matchName(name, members).ifPresent(id ->
                    elegibles.removeIf(member -> member.getUser().getId().equals(id)));
        }
        // Si las prohibiciones se llevan por delante a todo el equipo, no hay
        // reparto posible: se ignoran antes que devolver una tarea sin nadie.
        return elegibles.isEmpty() ? members : elegibles;
    }

    /** Lo que el modelo dijo de esa asignacion, si dijo algo aprovechable. */
    private String razonDelModelo(ProposedTask task) {
        String why = task.assignmentReason();
        return why == null || why.isBlank()
                ? "Es a quien propuso el modelo y la carga lo permite"
                : why.strip();
    }

    private String motivoDeReparto(ProposedTask task, List<TeamMember> todos, List<TeamMember> elegibles) {
        if (elegibles.size() < todos.size()) {
            return "Reparto: a quien menos carga lleva de los que pueden hacerla";
        }
        return task.assigneeName() == null || task.assigneeName().isBlank()
                ? "Reparto: el modelo no propuso a nadie, va a quien menos carga lleva"
                : "Reparto: a quien menos carga lleva";
    }

    /** A quien va la tarea y la frase que lo explica. */
    private record Decision(Long memberId, String why) {
    }

    /**
     * Cuanto puede cargar de mas quien encaja mejor antes de que el reparto se lo
     * quite: lo que dura una tarea media de esta propuesta.
     *
     * <p>Se mide sobre la propuesta y no con un numero fijo porque un plan de
     * tareas de dos horas y otro de tareas de quince no tienen la misma idea de
     * «una tarea de diferencia».</p>
     */
    private BigDecimal toleranceOf(List<ProposedTask> proposed) {
        if (proposed.isEmpty()) {
            return HORAS_POR_DEFECTO;
        }
        BigDecimal total = proposed.stream()
                .map(this::hoursOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(proposed.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Si sobran tareas para que todos tengan al menos una, nadie se queda a cero:
     * se le pasa la tarea mas pequena de quien mas acumula.
     */
    private void rebalanceEmptyMembers(List<ProposedTask> proposed, List<Long> owners, List<String> reasons,
                                       Map<Long, BigDecimal> hours, Map<Long, Integer> counts,
                                       List<TeamMember> members) {
        if (proposed.size() < counts.size()) return;

        for (Long empty : List.copyOf(counts.keySet())) {
            if (counts.get(empty) > 0) continue;

            Long donor = counts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (donor == null) return;

            int index = smallestTaskOf(proposed, owners, donor, empty, members);
            if (index < 0) return;

            BigDecimal moved = hoursOf(proposed.get(index));
            owners.set(index, empty);
            reasons.set(index, "Reparto: nadie se queda sin tareas");
            counts.merge(donor, -1, Integer::sum);
            counts.merge(empty, 1, Integer::sum);
            hours.merge(donor, moved.negate(), BigDecimal::add);
            hours.merge(empty, moved, BigDecimal::add);
        }
    }

    /**
     * La tarea mas pequena de quien mas acumula, entre las que el candidato puede
     * recibir: una prohibicion tampoco se salta por dejar a alguien sin trabajo.
     */
    private int smallestTaskOf(List<ProposedTask> proposed, List<Long> owners, Long owner,
                               Long candidate, List<TeamMember> members) {
        int best = -1;
        BigDecimal bestHours = null;
        for (int index = 0; index < proposed.size(); index++) {
            if (!owner.equals(owners.get(index))) continue;
            if (isForbidden(proposed.get(index), candidate, members)) continue;
            BigDecimal current = hoursOf(proposed.get(index));
            if (bestHours == null || current.compareTo(bestHours) < 0) {
                bestHours = current;
                best = index;
            }
        }
        return best;
    }

    /** Si esta tarea nombra al candidato entre los que no deben recibirla. */
    private boolean isForbidden(ProposedTask task, Long candidateId, List<TeamMember> members) {
        return task.avoidOrEmpty().stream()
                .anyMatch(name -> matchName(name, members)
                        .filter(candidateId::equals)
                        .isPresent());
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
        String normalized = TextNormalizer.plain(name);
        if (normalized.isBlank()) return Optional.empty();

        return members.stream()
                .filter(member -> {
                    String full = TextNormalizer.plain(member.getUser().getFullName());
                    String first = full.split(" ")[0];
                    return full.equals(normalized) || normalized.equals(first) || full.startsWith(normalized);
                })
                .map(member -> member.getUser().getId())
                .findFirst();
    }

    /**
     * Quien menos horas acumula de entre los candidatos.
     *
     * <p>Se limita a los candidatos y no a todo el equipo porque las prohibiciones
     * de una tarea pueden dejar fuera justo a quien menos carga lleva, y entonces
     * «el mas descargado» tiene que ser el mas descargado de los que si pueden
     * hacerla.</p>
     */
    private Long leastLoaded(Map<Long, BigDecimal> hours, List<TeamMember> candidates) {
        return candidates.stream()
                .map(member -> member.getUser().getId())
                .filter(hours::containsKey)
                .min(Comparator.comparing(hours::get))
                .orElseGet(() -> hours.entrySet().stream()
                        .min(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElseThrow());
    }

}
