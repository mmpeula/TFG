package com.tfg.kanban.security;

import com.tfg.kanban.TestSecurity;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.AttachmentRepository;
import com.tfg.kanban.domain.repository.CommentRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * El control de acceso es el nucleo del RBAC: se comprueba que cada rol
 * puede exactamente lo que debe sobre un proyecto.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessServiceTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private AttachmentRepository attachmentRepository;

    @InjectMocks
    private AccessService accessService;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAs(1L, "MIEMBRO");
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    private void withProjectRole(Role role) {
        when(teamMemberRepository.findRoleByProject(7L, 1L)).thenReturn(Optional.ofNullable(role));
    }

    @Test
    @DisplayName("El RESPONSABLE del equipo puede leer, escribir y administrar el proyecto")
    void responsableHasFullControl() {
        withProjectRole(Role.RESPONSABLE);

        assertThat(accessService.canReadProject(7L)).isTrue();
        assertThat(accessService.canWriteProject(7L)).isTrue();
        assertThat(accessService.canManageProject(7L)).isTrue();
    }

    @Test
    @DisplayName("El MIEMBRO lee y escribe pero no administra")
    void memberCannotManage() {
        withProjectRole(Role.MIEMBRO);

        assertThat(accessService.canReadProject(7L)).isTrue();
        assertThat(accessService.canWriteProject(7L)).isTrue();
        assertThat(accessService.canManageProject(7L)).isFalse();
    }

    @Test
    @DisplayName("El DOCENTE solo lee")
    void observerIsReadOnly() {
        withProjectRole(Role.DOCENTE);

        assertThat(accessService.canReadProject(7L)).isTrue();
        assertThat(accessService.canWriteProject(7L)).isFalse();
        assertThat(accessService.canManageProject(7L)).isFalse();
    }

    @Test
    @DisplayName("Quien no pertenece al equipo no puede ni leer")
    void outsiderHasNoAccess() {
        withProjectRole(null);

        assertThat(accessService.canReadProject(7L)).isFalse();
        assertThat(accessService.canWriteProject(7L)).isFalse();
        assertThat(accessService.canManageProject(7L)).isFalse();
    }

    @Test
    @DisplayName("El rol que cuenta es el del equipo, no el rol de la cuenta")
    void teamRoleWinsOverAccountRole() {
        // La cuenta es MIEMBRO (con la que nace todo el mundo) pero en este equipo
        // es la responsable: administrar el proyecto depende de lo segundo.
        TestSecurity.authenticateAs(1L, "MIEMBRO");
        withProjectRole(Role.RESPONSABLE);

        assertThat(accessService.canManageProject(7L)).isTrue();
    }

    // ------------------------------------------------ comentarios y adjuntos

    private Task taskInTeam() {
        Team team = Team.builder().id(3L).name("Equipo").build();
        Project project = Project.builder().id(7L).team(team).build();
        Board board = Board.builder().id(2L).project(project).build();
        BoardColumn column = BoardColumn.builder().id(5L).board(board).build();
        return Task.builder().id(11L).boardColumn(column).build();
    }

    @Test
    @DisplayName("El autor puede modificar su comentario; otro miembro no")
    void commentAuthorCanModify() {
        User author = User.builder().id(1L).build();
        User someoneElse = User.builder().id(2L).build();
        Task task = taskInTeam();

        when(commentRepository.findById(10L))
                .thenReturn(Optional.of(Comment.builder().id(10L).author(author).task(task).build()));
        when(commentRepository.findById(20L))
                .thenReturn(Optional.of(Comment.builder().id(20L).author(someoneElse).task(task).build()));
        when(teamMemberRepository.findRoleByTask(11L, 1L)).thenReturn(Optional.of(Role.MIEMBRO));

        assertThat(accessService.canModifyComment(10L)).isTrue();
        assertThat(accessService.canModifyComment(20L)).isFalse();
    }

    @Test
    @DisplayName("El RESPONSABLE del equipo puede modificar el comentario de cualquiera")
    void responsableCanModifyAnyComment() {
        Task task = taskInTeam();
        when(commentRepository.findById(20L)).thenReturn(Optional.of(
                Comment.builder().id(20L).author(User.builder().id(2L).build()).task(task).build()));
        when(teamMemberRepository.findRoleByTask(11L, 1L)).thenReturn(Optional.of(Role.RESPONSABLE));

        assertThat(accessService.canModifyComment(20L)).isTrue();
    }

    @Test
    @DisplayName("Un comentario o adjunto inexistente no da permisos")
    void missingResourcesDenyAccess() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());
        when(attachmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(accessService.canModifyComment(99L)).isFalse();
        assertThat(accessService.canReadComment(99L)).isFalse();
        assertThat(accessService.canReadAttachment(99L)).isFalse();
        assertThat(accessService.canDeleteAttachment(99L)).isFalse();
    }

    @Test
    @DisplayName("Quien subio el adjunto puede borrarlo; otro miembro no")
    void uploaderCanDeleteAttachment() {
        Task task = taskInTeam();
        when(attachmentRepository.findById(30L)).thenReturn(Optional.of(
                Attachment.builder().id(30L).uploadedBy(User.builder().id(1L).build()).task(task).build()));
        when(attachmentRepository.findById(31L)).thenReturn(Optional.of(
                Attachment.builder().id(31L).uploadedBy(User.builder().id(2L).build()).task(task).build()));
        when(teamMemberRepository.findRoleByTask(11L, 1L)).thenReturn(Optional.of(Role.MIEMBRO));

        assertThat(accessService.canDeleteAttachment(30L)).isTrue();
        assertThat(accessService.canDeleteAttachment(31L)).isFalse();
        assertThat(accessService.canReadAttachment(30L)).isTrue();
        assertThat(accessService.canReadComment(30L)).isFalse();
    }

    @Test
    @DisplayName("Sin usuario autenticado no hay permiso sobre comentarios ni adjuntos")
    void anonymousHasNoAccess() {
        TestSecurity.clear();

        assertThat(accessService.canModifyComment(10L)).isFalse();
        assertThat(accessService.canDeleteAttachment(30L)).isFalse();
        assertThat(accessService.roleInTeam(3L)).isEmpty();
    }

    @Test
    @DisplayName("Los permisos de tablero, columna y tarea siguen la misma regla de tres niveles")
    void boardColumnAndTaskPermissions() {
        when(teamMemberRepository.findRoleByBoard(2L, 1L)).thenReturn(Optional.of(Role.MIEMBRO));
        when(teamMemberRepository.findRoleByColumn(5L, 1L)).thenReturn(Optional.of(Role.DOCENTE));
        when(teamMemberRepository.findRoleByTask(11L, 1L)).thenReturn(Optional.of(Role.RESPONSABLE));

        assertThat(accessService.canReadBoard(2L)).isTrue();
        assertThat(accessService.canWriteBoard(2L)).isTrue();
        assertThat(accessService.canManageBoard(2L)).isFalse();

        assertThat(accessService.canReadColumn(5L)).isTrue();
        assertThat(accessService.canWriteColumn(5L)).isFalse();
        assertThat(accessService.canManageColumn(5L)).isFalse();

        assertThat(accessService.canReadTask(11L)).isTrue();
        assertThat(accessService.canWriteTask(11L)).isTrue();
        assertThat(accessService.canManageTask(11L)).isTrue();

        assertThat(accessService.isSelf(1L)).isTrue();
        assertThat(accessService.isSelf(2L)).isFalse();
    }

    @Test
    @DisplayName("isTeamMember refleja la pertenencia al equipo")
    void teamMembershipIsChecked() {
        Team team = Team.builder().id(3L).name("Equipo").build();
        User user = User.builder().id(1L).email("ana@uni.es").build();
        when(teamMemberRepository.findByTeam_IdAndUser_Id(3L, 1L))
                .thenReturn(Optional.of(TeamMember.builder().team(team).user(user).role(Role.MIEMBRO).build()));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(4L, 1L)).thenReturn(Optional.empty());

        assertThat(accessService.isTeamMember(3L)).isTrue();
        assertThat(accessService.canManageTeam(3L)).isFalse();
        assertThat(accessService.isTeamMember(4L)).isFalse();
    }
}
