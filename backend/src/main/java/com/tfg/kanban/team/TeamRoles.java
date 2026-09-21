package com.tfg.kanban.team;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;

/**
 * Que rol de equipo puede tener cada cuenta.
 *
 * <p>Vive aparte porque hacen falta en dos sitios que deben decir exactamente lo
 * mismo: al anadir a alguien directamente y al invitarle. Y en el caso de la
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

    /** El rol en el equipo tiene que encajar con lo que esa cuenta es en la plataforma. */
    static void ensureRoleMatchesAccount(User user, Role teamRole) {
        if (teamRole == Role.DOCENTE && user.getRole() != Role.DOCENTE) {
            throw new BusinessRuleException("Solo una cuenta DOCENTE puede seguir un equipo como docente");
        }
        if ((teamRole == Role.MIEMBRO || teamRole == Role.RESPONSABLE) && !isMemberAccount(user.getRole())) {
            throw new BusinessRuleException("Solo una cuenta MIEMBRO puede trabajar o ser responsable de un equipo");
        }
    }

    /** Permite que las cuentas antiguas RESPONSABLE sigan usando sus equipos. */
    private static boolean isMemberAccount(Role role) {
        return role == Role.MIEMBRO || role == Role.RESPONSABLE;
    }
}
