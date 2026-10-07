package com.tfg.kanban.team;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;

import java.util.List;
import java.util.stream.Stream;

/**
 * Que rol de equipo puede tener cada cuenta.
 *
 * <p>Vive aparte porque hace falta en varios sitios que deben decir exactamente lo
 * mismo: al cambiar el rol de alguien del equipo y al invitarle. Y en el caso de la
 * invitacion, dos veces: al mandarla, para no prometer algo imposible, y al
 * aceptarla, porque entre una cosa y otra un administrador ha podido cambiarle
 * el tipo de cuenta.</p>
 */
final class TeamRoles {

    private TeamRoles() {
    }

    /** RESPONSABLE, MIEMBRO y DOCENTE son de equipo; ADMIN es de plataforma y aqui no cabe. */
    static Role ensureTeamRole(Role role) {
        if (role != Role.RESPONSABLE && role != Role.MIEMBRO && role != Role.DOCENTE) {
            throw new BusinessRuleException("En un equipo solo se puede asignar RESPONSABLE, MIEMBRO o DOCENTE");
        }
        return role;
    }

    /**
     * Ser docente de un equipo se decide al invitar y ya no se cambia desde dentro:
     * ni quien sigue el equipo como docente pasa a trabajar en el, ni alguien que
     * trabaja pasa a solo mirar. Son dos papeles distintos (uno evalua, el otro es
     * evaluado), y cambiar de uno a otro sin que nadie lo vuelva a aceptar dejaria
     * a un docente con tareas del reparto, o a un alumno fuera de el sin avisar.
     * Si hace falta, se le saca del equipo y se le invita otra vez con el otro papel.
     */
    static void ensureDocenteRoleUnchanged(Role current, Role requested) {
        if (current == Role.DOCENTE && requested != Role.DOCENTE) {
            throw new BusinessRuleException("El rol de un docente no se puede cambiar: se elige al invitarle. "
                    + "Si tiene que trabajar en el equipo, sácale e invítale otra vez como alumno");
        }
        if (current != Role.DOCENTE && requested == Role.DOCENTE) {
            throw new BusinessRuleException("Nadie pasa a docente desde dentro del equipo: ese papel se elige al "
                    + "invitar. Sácale e invítale otra vez como docente");
        }
    }

    /**
     * El rol en el equipo tiene que encajar con lo que esa cuenta es en la plataforma.
     *
     * <p>El rol que cuenta en cada espacio es el que se tiene <b>en ese espacio</b>.
     * Una cuenta DOCENTE puede ser responsable de los que crea ella y a la vez seguir
     * como docente los de otras personas; lo que no puede hacer nadie que no sea
     * DOCENTE es seguir un equipo como docente, ni un administrador estar en ninguno.</p>
     */
    static void ensureRoleMatchesAccount(User user, Role teamRole) {
        if (matchesAccount(user, teamRole)) {
            return;
        }
        throw new BusinessRuleException(teamRole == Role.DOCENTE
                ? "Solo una cuenta DOCENTE puede seguir un equipo como docente"
                : "Solo una cuenta de alumno o de docente puede trabajar o ser responsable de un equipo");
    }

    private static boolean matchesAccount(User user, Role teamRole) {
        return switch (teamRole) {
            case DOCENTE -> user.getRole() == Role.DOCENTE;
            case MIEMBRO, RESPONSABLE -> canWorkInTeams(user.getRole());
            default -> false;
        };
    }

    /**
     * Con que roles se puede invitar a esta cuenta, el que le corresponde primero. Una cuenta
     * docente admite dos (seguir el equipo o trabajar en el); una de alumno, uno; una de
     * administrador, ninguno. La pantalla de invitar lo usa para ofrecer la eleccion solo
     * cuando existe.
     */
    static List<Role> inviteRolesFor(User user) {
        return Stream.of(entryRoleFor(user), Role.MIEMBRO, Role.DOCENTE)
                .distinct()
                .filter(role -> matchesAccount(user, role))
                .toList();
    }

    /**
     * El rol de equipo con el que entra alguien cuando quien invita no elige otro: el
     * que le corresponde a su cuenta. Una cuenta DOCENTE entra como docente (es lo que
     * es en el espacio de otra persona); cualquier otra, como alumno.
     *
     * <p>Quien invita puede pedir que una cuenta docente entre como alumno, para que
     * trabaje en ese equipo; lo que no puede pedir es RESPONSABLE ni un rol que la
     * cuenta no admita. Llegar a RESPONSABLE es un paso aparte, posterior: se cambia
     * despues desde la pantalla de miembros, no se promete en la invitacion.</p>
     */
    static Role entryRoleFor(User user) {
        return user.getRole() == Role.DOCENTE ? Role.DOCENTE : Role.MIEMBRO;
    }

    /** Alumno y docente trabajan en equipos; un administrador no. */
    private static boolean canWorkInTeams(Role role) {
        return role == Role.MIEMBRO || role == Role.DOCENTE;
    }
}
