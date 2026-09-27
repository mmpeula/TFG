package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.PlanOptions;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.TeamMember;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * El equipo tal y como entra en un reparto: quien, con que rol, cuantas horas y
 * con que preferencias.
 *
 * <p>Sale siempre de los miembros reales del workspace. El formulario puede quitar
 * a alguien de este reparto o ajustar sus datos, pero no meter a nadie de fuera:
 * una tarea para alguien que no esta en el equipo llegaria al tablero sin
 * responsable, porque solo se puede asignar a miembros.</p>
 */
final class PlanTeam {

    /** Lo que se asume si no se dice: una asignatura normal, no dedicacion completa. */
    static final int DEFAULT_HOURS_PER_WEEK = 10;

    /** Lo que se reserva de cada sprint para imprevistos: el modelo planifica sobre el resto. */
    private static final BigDecimal USEFUL_SHARE = new BigDecimal("0.9");

    /**
     * Una persona del reparto. {@code leader} marca a quien coordina el equipo: el
     * prompt le pasa la gestion y, con equipos grandes, le quita carga tecnica.
     */
    record Member(Long userId, String name, int hoursPerWeek, String preferences, boolean leader) {

        /** Horas que puede dedicar en un sprint de tantos dias. */
        BigDecimal capacityFor(long sprintDays) {
            return BigDecimal.valueOf(hoursPerWeek)
                    .multiply(BigDecimal.valueOf(sprintDays))
                    .divide(BigDecimal.valueOf(7), 1, RoundingMode.HALF_UP);
        }

        /**
         * Lo que se le pide al modelo que no supere: la capacidad sin el margen
         * para imprevistos. Se calcula aqui y no se le deja al modelo, que en
         * aritmetica se equivoca mas que en repartir.
         */
        BigDecimal usefulCapacityFor(long sprintDays) {
            return capacityFor(sprintDays).multiply(USEFUL_SHARE).setScale(1, RoundingMode.HALF_UP);
        }

        boolean available() {
            return hoursPerWeek > 0;
        }
    }

    private final List<Member> members;

    private PlanTeam(List<Member> members) {
        this.members = List.copyOf(members);
    }

    List<Member> members() {
        return members;
    }

    /**
     * Resuelve el equipo del reparto.
     *
     * @param teamMembers miembros del workspace
     * @param slots       lo que dice el formulario; null o vacio es "todo el equipo"
     */
    static PlanTeam resolve(List<TeamMember> teamMembers, List<PlanOptions.TeamSlot> slots) {
        // El docente solo sigue el equipo: no entra en ningun reparto.
        List<TeamMember> elegibles = teamMembers.stream()
                .filter(member -> member.getRole() != Role.DOCENTE)
                .toList();

        List<Member> result = new ArrayList<>();
        if (slots == null || slots.isEmpty()) {
            for (TeamMember member : elegibles) {
                result.add(from(member, null));
            }
        } else {
            Set<Long> vistos = new HashSet<>();
            for (PlanOptions.TeamSlot slot : slots) {
                if (slot == null || slot.userId() == null || !vistos.add(slot.userId())) continue;
                elegibles.stream()
                        .filter(member -> member.getUser().getId().equals(slot.userId()))
                        .findFirst()
                        .ifPresent(member -> result.add(from(member, slot)));
            }
        }

        if (result.isEmpty()) {
            throw new BusinessRuleException("No hay nadie del equipo en el reparto: incluye al menos a una persona");
        }
        return new PlanTeam(result);
    }

    private static Member from(TeamMember member, PlanOptions.TeamSlot slot) {
        int hours = slot == null || slot.hoursPerWeek() == null ? DEFAULT_HOURS_PER_WEEK : slot.hoursPerWeek();
        // Null es "no lo he tocado" y trae la ficha de Miembros; una cadena vacia es
        // que se ha borrado a proposito para este reparto, y se respeta.
        String preferences = slot == null || slot.preferences() == null
                ? member.getPlanNotes()
                : slot.preferences().strip();
        return new Member(member.getUser().getId(), member.getUser().getFullName(), hours,
                preferences == null ? "" : preferences, member.getRole() == Role.RESPONSABLE);
    }

    /**
     * Quien del equipo casa con el nombre que da el modelo: nombre completo exacto,
     * solo el nombre de pila, o que empiece por el texto dado. Sin tildes ni
     * mayusculas, porque el modelo las pone y las quita a su aire.
     */
    Optional<Member> match(String name) {
        String buscado = TextNormalizer.plain(name);
        if (buscado.isBlank()) return Optional.empty();

        return members.stream()
                .filter(member -> {
                    String completo = TextNormalizer.plain(member.name());
                    String pila = completo.split(" ")[0];
                    return completo.equals(buscado) || buscado.equals(pila) || completo.startsWith(buscado);
                })
                .findFirst();
    }

    /** Cada nombre se resuelve por separado con {@link #match}; sin duplicados, en el orden en que aparecen. */
    List<Member> matchAll(List<String> names) {
        LinkedHashSet<Member> resultado = new LinkedHashSet<>();
        for (String name : names) {
            match(name).ifPresent(resultado::add);
        }
        return List.copyOf(resultado);
    }
}
