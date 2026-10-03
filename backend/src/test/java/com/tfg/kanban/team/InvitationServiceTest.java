package com.tfg.kanban.team;

import com.tfg.kanban.TestSecurity;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.InvitationRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.domain.repository.TeamRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.team.dto.InviteRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que hay que proteger aqui es que nadie acabe en un equipo sin haber dicho
 * que si, y que responder una invitacion sea cosa exclusiva de quien la recibio.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvitationServiceTest {

    @Mock private InvitationRepository invitationRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private MailService mailService;

    private InvitationService service;
    private Team team;
    private User responsable;
    private User invitada;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAs(1L, "MIEMBRO");
        service = new InvitationService(invitationRepository, teamRepository, teamMemberRepository,
                userRepository, mailService);

        responsable = User.builder().id(1L).email("ana@uni.es").fullName("Ana Garcia").role(Role.MIEMBRO).build();
        invitada = User.builder().id(2L).email("luis@uni.es").fullName("Luis Perez").role(Role.MIEMBRO).build();
        team = Team.builder().id(5L).name("Grupo 4").owner(responsable).build();

        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.findById(1L)).thenReturn(Optional.of(responsable));
        when(userRepository.findByEmailIgnoreCase("luis@uni.es")).thenReturn(Optional.of(invitada));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));
        when(mailService.getBaseUrl()).thenReturn("http://localhost:3000");
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    private Invitation pendiente(Role role) {
        return Invitation.builder().id(9L).team(team).invited(invitada).invitedBy(responsable)
                .role(role).token("tok").status(InvitationStatus.PENDIENTE).build();
    }

    // ------------------------------------------------------------- invitar

    @Test
    @DisplayName("Invitar deja la invitacion pendiente y manda el correo, pero no mete a nadie en el equipo")
    void invitingDoesNotAddAnyoneYet() {
        var response = service.invite(5L, new InviteRequest("luis@uni.es", null));

        assertThat(response.status()).isEqualTo(InvitationStatus.PENDIENTE);
        assertThat(response.invited().email()).isEqualTo("luis@uni.es");
        verify(mailService).send(anyString(), anyString(), anyString());
        // Lo importante: nadie entra en el equipo al invitar.
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("El correo lleva el enlace con el token para responder")
    void theMailCarriesTheLink() {
        service.invite(5L, new InviteRequest("luis@uni.es", null));

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(anyString(), anyString(), cuerpo.capture());
        assertThat(cuerpo.getValue()).contains("http://localhost:3000/invitacion/");
        assertThat(cuerpo.getValue()).contains("Grupo 4").contains("Ana Garcia");
    }

    @Test
    @DisplayName("Si no hay ninguna cuenta con ese email, se dice claramente")
    void unknownEmailIsReported() {
        when(userRepository.findByEmailIgnoreCase("nadie@uni.es")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(5L, new InviteRequest("nadie@uni.es", null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No hay ninguna cuenta")
                .hasMessageContaining("nadie@uni.es");
    }

    @Test
    @DisplayName("No se invita a quien ya esta en el equipo")
    void cannotInviteSomeoneAlreadyIn() {
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(5L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.invite(5L, new InviteRequest("luis@uni.es", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya esta en el equipo");
    }

    @Test
    @DisplayName("No se invita dos veces a la misma persona mientras no responda")
    void cannotInviteTwiceWhilePending() {
        when(invitationRepository.existsByTeam_IdAndInvited_IdAndStatus(5L, 2L, InvitationStatus.PENDIENTE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.invite(5L, new InviteRequest("luis@uni.es", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("invitacion pendiente");
    }

    @Test
    @DisplayName("Sin elegir rol, una cuenta de alumno entra como alumno")
    void memberAccountsAreInvitedAsMiembro() {
        var response = service.invite(5L, new InviteRequest("luis@uni.es", null));

        assertThat(response.role()).isEqualTo(Role.MIEMBRO);
    }

    @Test
    @DisplayName("Sin elegir rol, a una cuenta DOCENTE se le invita como docente")
    void docenteAccountsAreInvitedAsDocenteByDefault() {
        invitada.setRole(Role.DOCENTE);

        var response = service.invite(5L, new InviteRequest("luis@uni.es", null));

        assertThat(response.role()).isEqualTo(Role.DOCENTE);
    }

    @Test
    @DisplayName("A una cuenta DOCENTE se la puede invitar como alumno, para que trabaje en el equipo")
    void docenteAccountsCanBeInvitedAsStudent() {
        invitada.setRole(Role.DOCENTE);

        var response = service.invite(5L, new InviteRequest("luis@uni.es", Role.MIEMBRO));

        assertThat(response.role()).isEqualTo(Role.MIEMBRO);
    }

    @Test
    @DisplayName("Una cuenta de alumno no se puede invitar como docente")
    void studentAccountsCannotBeInvitedAsDocente() {
        assertThatThrownBy(() -> service.invite(5L, new InviteRequest("luis@uni.es", Role.DOCENTE)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("DOCENTE");
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("No se invita directamente como RESPONSABLE ni como ADMIN: eso es un paso posterior, ya dentro")
    void cannotBeInvitedAsResponsableOrAdmin() {
        invitada.setRole(Role.DOCENTE);

        assertThatThrownBy(() -> service.invite(5L, new InviteRequest("luis@uni.es", Role.RESPONSABLE)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("paso posterior");
        assertThatThrownBy(() -> service.invite(5L, new InviteRequest("luis@uni.es", Role.ADMIN)))
                .isInstanceOf(BusinessRuleException.class);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Consultar un correo dice con que roles se puede invitar, el de su cuenta primero")
    void candidateListsTheAllowedRoles() {
        var student = service.candidate(" Luis@uni.es ");
        assertThat(student.allowedRoles()).containsExactly(Role.MIEMBRO);
        assertThat(student.defaultRole()).isEqualTo(Role.MIEMBRO);

        invitada.setRole(Role.DOCENTE);
        var teacher = service.candidate("luis@uni.es");
        assertThat(teacher.accountRole()).isEqualTo(Role.DOCENTE);
        assertThat(teacher.allowedRoles()).containsExactly(Role.DOCENTE, Role.MIEMBRO);
        assertThat(teacher.defaultRole()).isEqualTo(Role.DOCENTE);

        invitada.setRole(Role.ADMIN);
        var admin = service.candidate("luis@uni.es");
        assertThat(admin.allowedRoles()).isEmpty();
        assertThat(admin.defaultRole()).isNull();
    }

    @Test
    @DisplayName("Consultar un correo sin cuenta dice que no existe, como al invitarle")
    void candidateOfUnknownEmail() {
        assertThatThrownBy(() -> service.candidate("nadie@uni.es"))
                .isInstanceOf(com.tfg.kanban.common.ResourceNotFoundException.class)
                .hasMessageContaining("nadie@uni.es");
    }

    @Test
    @DisplayName("Si el correo no sale, la invitacion se guarda igual y se dice que no se envio")
    void theInvitationSurvivesAFailingMailServer() {
        when(mailService.send(anyString(), anyString(), anyString())).thenReturn(false);

        var response = service.invite(5L, new InviteRequest("luis@uni.es", null));

        assertThat(response.emailSent()).isFalse();
        assertThat(response.status()).isEqualTo(InvitationStatus.PENDIENTE);
    }

    // ------------------------------------------------------------ responder

    @Test
    @DisplayName("Al aceptar, y solo entonces, la persona entra en el equipo con el rol de la invitacion")
    void acceptingJoinsTheTeam() {
        TestSecurity.authenticateAs(2L, "MIEMBRO");
        Invitation invitation = pendiente(Role.RESPONSABLE);
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(i -> i.getArgument(0));

        var member = service.accept("tok");

        assertThat(member.role()).isEqualTo(Role.RESPONSABLE);
        assertThat(member.user().id()).isEqualTo(2L);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACEPTADA);
        assertThat(invitation.getRespondedAt()).isNotNull();
    }

    @Test
    @DisplayName("Una invitacion ajena no se puede aceptar, y no se delata que exista")
    void cannotAcceptSomeoneElsesInvitation() {
        TestSecurity.authenticateAs(99L, "MIEMBRO");
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(pendiente(Role.MIEMBRO)));

        assertThatThrownBy(() -> service.accept("tok"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no existe o ya no es valida");
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una invitacion ya respondida no se puede volver a responder")
    void cannotAnswerTwice() {
        TestSecurity.authenticateAs(2L, "MIEMBRO");
        Invitation invitation = pendiente(Role.MIEMBRO);
        invitation.setStatus(InvitationStatus.ACEPTADA);
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.accept("tok"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya esta respondida");
    }

    @Test
    @DisplayName("Si entre medias la hacen administradora, el rol prometido ya no vale: un administrador no entra en equipos")
    void theRoleIsCheckedAgainOnAccept() {
        TestSecurity.authenticateAs(2L, "ADMIN");
        invitada.setRole(Role.ADMIN);
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(pendiente(Role.MIEMBRO)));

        assertThatThrownBy(() -> service.accept("tok"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("alumno o de docente");
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Si entre medias la hacen docente, la invitacion como alumno sigue valiendo: un docente tambien trabaja en equipos")
    void promotedToTeacherCanStillAcceptAStudentInvitation() {
        TestSecurity.authenticateAs(2L, "DOCENTE");
        invitada.setRole(Role.DOCENTE);
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(pendiente(Role.MIEMBRO)));
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.accept("tok").role()).isEqualTo(Role.MIEMBRO);
    }

    @Test
    @DisplayName("Rechazar la deja cerrada y sin meter a nadie")
    void rejectingClosesIt() {
        TestSecurity.authenticateAs(2L, "MIEMBRO");
        Invitation invitation = pendiente(Role.MIEMBRO);
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));

        service.reject("tok");

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.RECHAZADA);
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cancelar una invitacion de otro equipo no cuela")
    void cannotCancelAnInvitationFromAnotherTeam() {
        when(invitationRepository.findById(9L)).thenReturn(Optional.of(pendiente(Role.MIEMBRO)));

        assertThatThrownBy(() -> service.cancel(777L, 9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Las pendientes del equipo son las que salen en la pantalla de miembros")
    void listsPendingOnes() {
        when(invitationRepository.findByTeam_IdAndStatusOrderByCreatedAtAsc(5L, InvitationStatus.PENDIENTE))
                .thenReturn(List.of(pendiente(Role.MIEMBRO)));

        assertThat(service.pendingOf(5L)).singleElement()
                .satisfies(i -> assertThat(i.invited().email()).isEqualTo("luis@uni.es"));
    }
}
