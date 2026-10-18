package com.tfg.kanban.team;

import com.tfg.kanban.TestSecurity;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.Team;
import com.tfg.kanban.domain.model.TeamMember;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.domain.repository.TeamRepository;
import com.tfg.kanban.task.TaskService;
import com.tfg.kanban.team.dto.UpdateMemberNotesRequest;
import com.tfg.kanban.team.dto.UpdateMemberRoleRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitarios de las reglas de equipos y de su composicion. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TaskService taskService;

    @InjectMocks
    private TeamService teamService;

    private User owner;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAs(1L, "MIEMBRO");
        owner = User.builder().id(1L).email("ana@uni.es").fullName("Ana Garcia").role(Role.MIEMBRO).build();
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("Las indicaciones para el plan se guardan en la pertenencia al equipo")
    void planNotesArePersisted() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        TeamMember membership = TeamMember.builder().id(9L).team(team).user(owner).role(Role.MIEMBRO).build();
        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 1L)).thenReturn(Optional.of(membership));

        var response = teamService.updateMemberNotes(5L, 1L,
                new UpdateMemberNotesRequest("  Se maneja con Java y Spring. Evitar frontend.  "));

        assertThat(membership.getPlanNotes()).isEqualTo("Se maneja con Java y Spring. Evitar frontend.");
        assertThat(response.planNotes()).isEqualTo("Se maneja con Java y Spring. Evitar frontend.");
    }

    @Test
    @DisplayName("Un texto en blanco borra las indicaciones en vez de dejarlas vacias")
    void blankNotesClearTheField() {
        // «Sin indicaciones» tiene que ser un unico estado: null. Si no, el prompt
        // tendria que distinguir entre null y "" para decidir si hay ficha o no.
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        TeamMember membership = TeamMember.builder().id(9L).team(team).user(owner)
                .role(Role.MIEMBRO).planNotes("Lo de antes").build();
        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 1L)).thenReturn(Optional.of(membership));

        teamService.updateMemberNotes(5L, 1L, new UpdateMemberNotesRequest("   "));
        assertThat(membership.getPlanNotes()).isNull();

        teamService.updateMemberNotes(5L, 1L, new UpdateMemberNotesRequest(null));
        assertThat(membership.getPlanNotes()).isNull();
    }

    @Test
    @DisplayName("Cambiar el rol no toca las indicaciones, y al reves tampoco")
    void notesAndRoleAreIndependent() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        TeamMember membership = TeamMember.builder().id(9L).team(team).user(owner)
                .role(Role.MIEMBRO).planNotes("Backend y Java").build();
        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 1L)).thenReturn(Optional.of(membership));

        teamService.updateMemberRole(5L, 1L, new UpdateMemberRoleRequest(Role.RESPONSABLE));
        assertThat(membership.getPlanNotes()).isEqualTo("Backend y Java");

        teamService.updateMemberNotes(5L, 1L, new UpdateMemberNotesRequest("Ahora otra cosa"));
        assertThat(membership.getRole()).isEqualTo(Role.RESPONSABLE);
    }

    @Test
    @DisplayName("Degradar al ultimo RESPONSABLE del equipo no esta permitido")
    void cannotDemoteLastResponsable() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        TeamMember membership = TeamMember.builder().id(9L).team(team).user(owner).role(Role.RESPONSABLE).build();

        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 1L)).thenReturn(Optional.of(membership));
        when(teamMemberRepository.countByTeam_IdAndRole(5L, Role.RESPONSABLE)).thenReturn(1L);

        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest(Role.MIEMBRO);

        assertThatThrownBy(() -> teamService.updateMemberRole(5L, 1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sin responsable");
    }

    @Test
    @DisplayName("El creador tampoco puede salirse si es el unico responsable: el equipo se quedaria sin nadie que lo administre")
    void theOwnerCannotLeaveAsTheOnlyResponsable() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        TeamMember membership = TeamMember.builder().id(9L).team(team).user(owner).role(Role.RESPONSABLE).build();
        team.getMembers().add(membership);

        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 1L)).thenReturn(Optional.of(membership));
        when(teamMemberRepository.countByTeam_IdAndRole(5L, Role.RESPONSABLE)).thenReturn(1L);

        assertThatThrownBy(() -> teamService.removeMember(5L, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sin responsable");
        // Si no se va, sus tareas siguen siendo suyas.
        verify(taskService, never()).releaseOpenTasks(anyLong(), any());
    }

    @Test
    @DisplayName("El creador si puede salirse si deja a otro responsable, y el equipo pasa a ser de esa persona")
    void theOwnerCanLeaveHandingOverTheTeam() {
        User otra = User.builder().id(2L).email("luis@uni.es").fullName("Luis Perez").build();
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        TeamMember suya = TeamMember.builder().id(9L).team(team).user(owner).role(Role.RESPONSABLE).build();
        TeamMember laOtra = TeamMember.builder().id(10L).team(team).user(otra).role(Role.RESPONSABLE).build();
        team.getMembers().add(suya);
        team.getMembers().add(laOtra);

        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 1L)).thenReturn(Optional.of(suya));
        when(teamMemberRepository.countByTeam_IdAndRole(5L, Role.RESPONSABLE)).thenReturn(2L);

        teamService.removeMember(5L, 1L);

        // Sin traspaso, el equipo se quedaria a nombre de alguien que ya no esta y
        // nadie podria borrarlo al quedarse vacio.
        assertThat(team.getOwner()).isEqualTo(otra);
        assertThat(team.getMembers()).containsExactly(laOtra);
    }

    @Test
    @DisplayName("ADMIN es un rol de plataforma: no se puede dar como rol de equipo")
    void adminIsNotATeamRole() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        User member = User.builder().id(2L).fullName("Luis Perez").role(Role.MIEMBRO).build();
        TeamMember membership = TeamMember.builder().id(9L).team(team).user(member).role(Role.MIEMBRO).build();
        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 2L)).thenReturn(Optional.of(membership));

        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest(Role.ADMIN);

        assertThatThrownBy(() -> teamService.updateMemberRole(5L, 2L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("En un equipo solo se puede asignar");
    }

    @Test
    @DisplayName("Un docente del equipo no puede pasar a alumno ni a responsable: se elige al invitar")
    void docenteRoleCannotChange() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        User profe = User.builder().id(2L).fullName("Ana Profe").role(Role.DOCENTE).build();
        TeamMember membership = TeamMember.builder().id(9L).team(team).user(profe).role(Role.DOCENTE).build();

        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 2L)).thenReturn(Optional.of(membership));

        for (Role nuevo : List.of(Role.MIEMBRO, Role.RESPONSABLE)) {
            UpdateMemberRoleRequest request = new UpdateMemberRoleRequest(nuevo);
            assertThatThrownBy(() -> teamService.updateMemberRole(5L, 2L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("El rol de un docente no se puede cambiar");
        }
        assertThat(membership.getRole()).isEqualTo(Role.DOCENTE);
    }

    @Test
    @DisplayName("Quien trabaja en el equipo no pasa a docente, aunque su cuenta lo sea")
    void cannotBecomeDocenteFromInside() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        User profe = User.builder().id(2L).fullName("Ana Profe").role(Role.DOCENTE).build();
        TeamMember membership = TeamMember.builder().id(9L).team(team).user(profe).role(Role.MIEMBRO).build();

        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 2L)).thenReturn(Optional.of(membership));

        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest(Role.DOCENTE);
        assertThatThrownBy(() -> teamService.updateMemberRole(5L, 2L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Nadie pasa a docente");
        assertThat(membership.getRole()).isEqualTo(Role.MIEMBRO);
    }

    @Test
    @DisplayName("Ascender a alguien a RESPONSABLE no comprueba el ultimo responsable")
    void promotingDoesNotCheckLastResponsable() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        User member = User.builder().id(2L).fullName("Luis Perez").build();
        TeamMember membership = TeamMember.builder().id(9L).team(team).user(member).role(Role.MIEMBRO).build();

        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 2L)).thenReturn(Optional.of(membership));

        var response = teamService.updateMemberRole(5L, 2L, new UpdateMemberRoleRequest(Role.RESPONSABLE));

        assertThat(response.role()).isEqualTo(Role.RESPONSABLE);
        assertThat(membership.getRole()).isEqualTo(Role.RESPONSABLE);
    }

    @Test
    @DisplayName("Un MIEMBRO puede salirse del equipo sin mas comprobaciones")
    void memberCanLeaveTeam() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        User member = User.builder().id(2L).fullName("Luis Perez").build();
        TeamMember suya = TeamMember.builder().id(8L).team(team).user(owner).role(Role.RESPONSABLE).build();
        TeamMember membership = TeamMember.builder().id(9L).team(team).user(member).role(Role.MIEMBRO).build();
        team.getMembers().add(suya);
        team.getMembers().add(membership);

        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 2L)).thenReturn(Optional.of(membership));

        teamService.removeMember(5L, 2L);

        assertThat(team.getMembers()).containsExactly(suya);
    }

    @Test
    @DisplayName("Quien se va del equipo suelta sus tareas sin terminar")
    void leavingReleasesOpenTasks() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        User member = User.builder().id(2L).fullName("Luis Perez").build();
        TeamMember suya = TeamMember.builder().id(8L).team(team).user(owner).role(Role.RESPONSABLE).build();
        TeamMember membership = TeamMember.builder().id(9L).team(team).user(member).role(Role.MIEMBRO).build();
        team.getMembers().add(suya);
        team.getMembers().add(membership);

        when(teamMemberRepository.findByTeam_IdAndUser_Id(5L, 2L)).thenReturn(Optional.of(membership));

        teamService.removeMember(5L, 2L);

        verify(taskService).releaseOpenTasks(5L, member);
    }
}
