package com.tfg.kanban.security;

import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.repository.CommentRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * Reglas de autorizacion a nivel de recurso, usadas desde {@code @PreAuthorize}
 * con el nombre de bean {@code access}: por ejemplo
 * {@code @PreAuthorize("@access.canManageProject(#projectId)")}.
 *
 * <p>La comprobacion no mira el rol global del usuario sino el rol que tiene
 * <b>en el equipo</b> dueno del recurso (entidad {@code TeamMember}). Si el
 * usuario no pertenece a ese equipo, no tiene ningun permiso sobre el recurso.</p>
 *
 * <ul>
 *   <li><b>Leer</b>: cualquier miembro del equipo (los tres roles).</li>
 *   <li><b>Escribir</b> (tareas, comentarios y el asistente): RESPONSABLE y MIEMBRO.</li>
 *   <li><b>Administrar</b> (equipo, proyecto, tableros, miembros): solo RESPONSABLE.</li>
 * </ul>
 */
@Component("access")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessService {

    private static final Set<Role> WRITERS = Set.of(Role.RESPONSABLE, Role.MIEMBRO);

    private final TeamMemberRepository teamMemberRepository;
    private final CommentRepository commentRepository;

    /**
     * Rol en el equipo, vacio si el workspace esta suspendido.
     *
     * <p>Un equipo con su workspace parado se comporta como si no se perteneciera
     * a el: ni se ve, ni se invita, ni se cambian roles. Se filtra aqui, en la
     * consulta, y no en cada endpoint, porque es el unico sitio por el que pasan
     * todos los permisos y asi no queda ninguno sin cubrir.</p>
     */
    public Optional<Role> roleInTeam(Long teamId) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> teamMemberRepository.findActiveRoleInTeam(teamId, userId));
    }

    public Optional<Role> roleInProject(Long projectId) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> teamMemberRepository.findRoleByProject(projectId, userId));
    }

    public Optional<Role> roleInBoard(Long boardId) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> teamMemberRepository.findRoleByBoard(boardId, userId));
    }

    public Optional<Role> roleInColumn(Long columnId) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> teamMemberRepository.findRoleByColumn(columnId, userId));
    }

    public Optional<Role> roleInTask(Long taskId) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> teamMemberRepository.findRoleByTask(taskId, userId));
    }

    public boolean isTeamMember(Long teamId) {
        return roleInTeam(teamId).isPresent();
    }

    public boolean canManageTeam(Long teamId) {
        return roleInTeam(teamId).filter(Role.RESPONSABLE::equals).isPresent();
    }

    public boolean canReadProject(Long projectId) {
        return roleInProject(projectId).isPresent();
    }

    public boolean canManageProject(Long projectId) {
        return roleInProject(projectId).filter(Role.RESPONSABLE::equals).isPresent();
    }

    /**
     * Trabajar en el proyecto: lo mismo que escribir en sus tareas. Es lo que pide el
     * asistente, que esta para ayudar a hacer el trabajo; el docente solo lo sigue.
     */
    public boolean canWriteProject(Long projectId) {
        return roleInProject(projectId).filter(WRITERS::contains).isPresent();
    }

    public boolean canReadBoard(Long boardId) {
        return roleInBoard(boardId).isPresent();
    }

    public boolean canWriteColumn(Long columnId) {
        return roleInColumn(columnId).filter(WRITERS::contains).isPresent();
    }

    public boolean canReadTask(Long taskId) {
        return roleInTask(taskId).isPresent();
    }

    public boolean canWriteTask(Long taskId) {
        return roleInTask(taskId).filter(WRITERS::contains).isPresent();
    }

    /**
     * El autor puede editar o borrar su comentario; el RESPONSABLE del equipo, cualquiera.
     *
     * <p>En los dos casos hay que seguir en el equipo, con el espacio sin suspender: ser el
     * autor no basta. Quien se ha ido del equipo (o lo han sacado) ya no toca lo que dejo
     * escrito, igual que ya no ve la tarea.</p>
     */
    public boolean canModifyComment(Long commentId) {
        Long userId = SecurityUtils.currentUserId().orElse(null);
        if (userId == null) {
            return false;
        }
        return commentRepository.findById(commentId)
                .flatMap(comment -> roleInTask(comment.getTask().getId())
                        .map(role -> role == Role.RESPONSABLE
                                || (WRITERS.contains(role) && comment.getAuthor().getId().equals(userId))))
                .orElse(false);
    }

    /** Cada usuario puede editar su propio perfil. */
    public boolean isSelf(Long userId) {
        return SecurityUtils.currentUserId().filter(userId::equals).isPresent();
    }

    /**
     * Coincidir en un equipo (en cualquiera, con cualquier rol) es lo que hace
     * falta para ver la foto de perfil de otra persona: no mas que lo que ya se
     * ve de ella en la lista de miembros, los comentarios o las tareas asignadas.
     */
    public boolean sharesTeamWith(Long otherUserId) {
        return SecurityUtils.currentUserId()
                .filter(userId -> !userId.equals(otherUserId))
                .map(userId -> teamMemberRepository.sharesTeam(userId, otherUserId))
                .orElse(false);
    }
}
