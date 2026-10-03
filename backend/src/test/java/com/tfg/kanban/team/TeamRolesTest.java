package com.tfg.kanban.team;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Que rol de equipo puede tener cada tipo de cuenta. */
class TeamRolesTest {

    private static User account(Role role) {
        return User.builder().id(1L).email("a@uni.es").role(role).build();
    }

    @Test
    @DisplayName("Un docente puede ser RESPONSABLE de sus espacios y DOCENTE en los de otros")
    void teacherCanLeadAndFollow() {
        assertThatCode(() -> TeamRoles.ensureRoleMatchesAccount(account(Role.DOCENTE), Role.RESPONSABLE)).doesNotThrowAnyException();
        assertThatCode(() -> TeamRoles.ensureRoleMatchesAccount(account(Role.DOCENTE), Role.MIEMBRO)).doesNotThrowAnyException();
        assertThatCode(() -> TeamRoles.ensureRoleMatchesAccount(account(Role.DOCENTE), Role.DOCENTE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un alumno trabaja o es responsable, pero no sigue un equipo como docente")
    void studentCannotBeATeacherInATeam() {
        assertThatCode(() -> TeamRoles.ensureRoleMatchesAccount(account(Role.MIEMBRO), Role.RESPONSABLE)).doesNotThrowAnyException();
        assertThatCode(() -> TeamRoles.ensureRoleMatchesAccount(account(Role.MIEMBRO), Role.MIEMBRO)).doesNotThrowAnyException();
        assertThatThrownBy(() -> TeamRoles.ensureRoleMatchesAccount(account(Role.MIEMBRO), Role.DOCENTE))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("DOCENTE");
    }

    @Test
    @DisplayName("Un administrador no pertenece a ningun equipo")
    void adminHasNoTeamRole() {
        for (Role teamRole : new Role[] {Role.RESPONSABLE, Role.MIEMBRO, Role.DOCENTE}) {
            assertThatThrownBy(() -> TeamRoles.ensureRoleMatchesAccount(account(Role.ADMIN), teamRole))
                    .isInstanceOf(BusinessRuleException.class);
        }
        assertThatThrownBy(() -> TeamRoles.ensureTeamRole(Role.ADMIN)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Con que roles se puede invitar a cada cuenta: docente dos, alumno uno, administrador ninguno")
    void inviteRolesPerAccount() {
        assertThat(TeamRoles.inviteRolesFor(account(Role.DOCENTE))).containsExactly(Role.DOCENTE, Role.MIEMBRO);
        assertThat(TeamRoles.inviteRolesFor(account(Role.MIEMBRO))).containsExactly(Role.MIEMBRO);
        assertThat(TeamRoles.inviteRolesFor(account(Role.ADMIN))).isEmpty();
    }

    @Test
    @DisplayName("Al aceptar una invitacion, un docente entra como docente y cualquier otro como alumno")
    void entryRoleFollowsTheAccount() {
        assertThat(TeamRoles.entryRoleFor(account(Role.DOCENTE))).isEqualTo(Role.DOCENTE);
        assertThat(TeamRoles.entryRoleFor(account(Role.MIEMBRO))).isEqualTo(Role.MIEMBRO);
    }
}
