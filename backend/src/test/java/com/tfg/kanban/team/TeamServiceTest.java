package com.tfg.kanban.team;

import com.tfg.kanban.TestSecurity;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.Team;
import com.tfg.kanban.domain.model.TeamMember;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.domain.repository.TeamRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.team.dto.AddMemberRequest;
import com.tfg.kanban.team.dto.TeamRequest;
import com.tfg.kanban.team.dto.UpdateMemberRoleRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private UserRepository userRepository;

    @InjectMocks
    private TeamService teamService;

    private User owner;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAs(1L, "MIEMBRO");
        owner = User.builder().id(1L).email("ana@uni.es").fullName("Ana Garcia").role(Role.MIEMBRO).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("Al crear un equipo su creador entra como RESPONSABLE")
    void creatorBecomesResponsable() {
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team team = invocation.getArgument(0);
            team.setId(5L);
            return team;
        });

        var response = teamService.create(new TeamRequest("Grupo 4", "Practicas"));

        ArgumentCaptor<Team> captor = ArgumentCaptor.forClass(Team.class);
        org.mockito.Mockito.verify(teamRepository).save(captor.capture());

        Team saved = captor.getValue();
        assertThat(saved.getOwner()).isEqualTo(owner);
        assertThat(saved.getMembers()).hasSize(1);
        assertThat(saved.getMembers().get(0).getRole()).isEqualTo(Role.RESPONSABLE);
        assertThat(response.myRole()).isEqualTo(Role.RESPONSABLE);
    }

    @Test
    @DisplayName("No se puede anadir dos veces al mismo usuario")
    void cannotAddSameMemberTwice() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        User invited = User.builder().id(2L).email("luis@uni.es").fullName("Luis Perez").build();

        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.findByEmailIgnoreCase("luis@uni.es")).thenReturn(Optional.of(invited));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(5L, 2L)).thenReturn(true);

        AddMemberRequest request = new AddMemberRequest("luis@uni.es", Role.MIEMBRO);

        assertThatThrownBy(() -> teamService.addMember(5L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya pertenece");
    }

    @Test
    @DisplayName("Invitar a un email no registrado devuelve 404")
    void cannotInviteUnknownEmail() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.findByEmailIgnoreCase("nadie@uni.es")).thenReturn(Optional.empty());

        AddMemberRequest request = new AddMemberRequest("nadie@uni.es", Role.MIEMBRO);

        assertThatThrownBy(() -> teamService.addMember(5L, request))
                .isInstanceOf(ResourceNotFoundException.class);
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
    @DisplayName("ADMIN es un rol de plataforma: no se puede invitar a nadie con el")
    void cannotInviteMemberAsAdmin() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        User invited = User.builder().id(2L).email("luis@uni.es").fullName("Luis Perez").build();

        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.findByEmailIgnoreCase("luis@uni.es")).thenReturn(Optional.of(invited));

        AddMemberRequest request = new AddMemberRequest("luis@uni.es", Role.ADMIN);

        assertThatThrownBy(() -> teamService.addMember(5L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("En un equipo solo se puede asignar");
    }

    @Test
    @DisplayName("Si no se indica rol al invitar, la persona entra como MIEMBRO")
    void addMemberDefaultsToMiembro() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        User invited = User.builder().id(2L).email("luis@uni.es").fullName("Luis Perez").build();

        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.findByEmailIgnoreCase("luis@uni.es")).thenReturn(Optional.of(invited));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(5L, 2L)).thenReturn(false);
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(i -> i.getArgument(0));

        var response = teamService.addMember(5L, new AddMemberRequest("luis@uni.es", null));

        assertThat(response.role()).isEqualTo(Role.MIEMBRO);
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
    @DisplayName("El detalle indica myRole nulo si el usuario no es del equipo")
    void detailReportsNullRoleForOutsiders() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        User other = User.builder().id(2L).fullName("Luis Perez").build();
        team.getMembers().add(TeamMember.builder().team(team).user(other).role(Role.MIEMBRO).build());

        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));

        assertThat(teamService.findById(5L).myRole()).isNull();
    }

    @Test
    @DisplayName("Renombrar el equipo conserva el resto de datos")
    void updateTeam() {
        Team team = Team.builder().id(5L).name("Grupo 4").description("Antigua").owner(owner).build();
        team.getMembers().add(TeamMember.builder().team(team).user(owner).role(Role.RESPONSABLE).build());
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));

        var response = teamService.update(5L, new TeamRequest("  Grupo 4 renombrado  ", "Nueva"));

        assertThat(response.name()).isEqualTo("Grupo 4 renombrado");
        assertThat(response.description()).isEqualTo("Nueva");
        assertThat(response.myRole()).isEqualTo(Role.RESPONSABLE);
    }

    @Test
    @DisplayName("Un equipo sin proyectos se borra sin problemas")
    void deleteEmptyTeam() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));

        teamService.delete(5L);

        org.mockito.Mockito.verify(teamRepository).delete(team);
    }

    @Test
    @DisplayName("No se borra un equipo que todavia tiene proyectos")
    void cannotDeleteTeamWithProjects() {
        Team team = Team.builder().id(5L).name("Grupo 4").owner(owner).build();
        team.getProjects().add(com.tfg.kanban.domain.model.Project.builder().id(1L).name("Practica").build());
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> teamService.delete(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("proyectos");
    }
}
