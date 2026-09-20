package com.tfg.kanban.team;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.Team;
import com.tfg.kanban.domain.model.TeamMember;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.domain.repository.TeamRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.team.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Equipos y sus miembros.
 *
 * Quien crea el equipo queda automaticamente como RESPONSABLE del mismo, de forma
 * que siempre hay al menos una persona que puede administrarlo.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public PageResponse<TeamResponse> findMine(Pageable pageable) {
        Long userId = currentUserId();
        return PageResponse.of(teamRepository.findByMembers_User_Id(userId, pageable),
                team -> TeamResponse.from(team, roleOf(team, userId)));
    }

    public TeamResponse findById(Long teamId) {
        Team team = getTeam(teamId);
        return TeamResponse.from(team, roleOf(team, currentUserId()));
    }

    @Transactional
    public TeamResponse create(TeamRequest request) {
        User owner = userRepository.findById(currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", currentUserId()));

        Team team = Team.builder()
                .name(request.name().trim())
                .description(request.description())
                .owner(owner)
                .build();

        // El creador entra en el equipo como RESPONSABLE.
        team.getMembers().add(TeamMember.builder()
                .team(team)
                .user(owner)
                .role(Role.RESPONSABLE)
                .build());

        Team saved = teamRepository.save(team);
        return TeamResponse.from(saved, Role.RESPONSABLE);
    }

    @Transactional
    public TeamResponse update(Long teamId, TeamRequest request) {
        Team team = getTeam(teamId);
        team.setName(request.name().trim());
        team.setDescription(request.description());
        return TeamResponse.from(team, roleOf(team, currentUserId()));
    }

    @Transactional
    public void delete(Long teamId) {
        Team team = getTeam(teamId);
        if (!team.getProjects().isEmpty()) {
            throw new BusinessRuleException(
                    "El equipo tiene proyectos asociados; borralos antes de eliminar el equipo");
        }
        teamRepository.delete(team);
    }

    public List<TeamMemberResponse> findMembers(Long teamId) {
        getTeam(teamId);
        return teamMemberRepository.findByTeam_IdOrderByUser_FullNameAsc(teamId).stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    @Transactional
    public TeamMemberResponse addMember(Long teamId, AddMemberRequest request) {
        Team team = getTeam(teamId);
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay ningun usuario registrado con el email " + email));

        if (teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, user.getId())) {
            throw new BusinessRuleException("El usuario ya pertenece al equipo");
        }

        Role teamRole = ensureTeamRole(request.role() == null ? Role.MIEMBRO : request.role());
        ensureRoleMatchesAccount(user, teamRole);
        TeamMember member = TeamMember.builder()
                .team(team)
                .user(user)
                .role(teamRole)
                .build();

        return TeamMemberResponse.from(teamMemberRepository.save(member));
    }

    @Transactional
    public TeamMemberResponse updateMemberRole(Long teamId, Long userId, UpdateMemberRoleRequest request) {
        TeamMember member = getMember(teamId, userId);
        Role role = ensureTeamRole(request.role());
        ensureRoleMatchesAccount(member.getUser(), role);

        if (member.getRole() == Role.RESPONSABLE && role != Role.RESPONSABLE) {
            ensureNotLastResponsable(teamId);
        }
        member.setRole(role);
        return TeamMemberResponse.from(member);
    }

    /**
     * RESPONSABLE es una responsabilidad dentro del equipo, no un rol de cuenta.
     */
    private Role ensureTeamRole(Role role) {
        if (role != Role.RESPONSABLE && role != Role.MIEMBRO && role != Role.DOCENTE) {
            throw new BusinessRuleException("En un equipo solo se puede asignar RESPONSABLE, MIEMBRO o DOCENTE");
        }
        return role;
    }

    private void ensureRoleMatchesAccount(User user, Role teamRole) {
        if (teamRole == Role.DOCENTE && user.getRole() != Role.DOCENTE) {
            throw new BusinessRuleException("Solo una cuenta DOCENTE puede seguir un equipo como docente");
        }
        if ((teamRole == Role.MIEMBRO || teamRole == Role.RESPONSABLE) && !isMemberAccount(user.getRole())) {
            throw new BusinessRuleException("Solo una cuenta MIEMBRO puede trabajar o ser responsable de un equipo");
        }
    }

    /** Permite que las cuentas antiguas RESPONSABLE sigan usando sus equipos. */
    private boolean isMemberAccount(Role role) {
        return role == Role.MIEMBRO || role == Role.RESPONSABLE;
    }

    /**
     * Saca a alguien del equipo, tanto si lo expulsa el responsable como si se sale
     * por su cuenta.
     *
     * <p>La regla que manda es que el equipo nunca se quede sin responsable: sin
     * nadie que pueda administrarlo, su proyecto queda inaccesible para siempre. A
     * quien crea el equipo no se le trata distinto por serlo —tambien puede irse—,
     * pero al hacerlo hay que pasarle la propiedad a otra persona responsable,
     * porque de ella depende quien puede borrar el equipo al quedarse vacio.</p>
     */
    @Transactional
    public void removeMember(Long teamId, Long userId) {
        TeamMember member = getMember(teamId, userId);
        Team team = member.getTeam();

        if (member.getRole() == Role.RESPONSABLE) {
            ensureNotLastResponsable(teamId);
        }
        if (team.getOwner() != null && team.getOwner().getId().equals(userId)) {
            team.setOwner(otroResponsable(team, userId));
        }
        // Por la coleccion y no por el repositorio: Team.members va con cascade ALL y
        // orphanRemoval, asi que si la lista en memoria sigue conteniendo al miembro
        // (basta con haberla leido, como hace otroResponsable), al volcar los cambios
        // Hibernate lo vuelve a guardar y el borrado se deshace sin dar ningun error.
        team.getMembers().remove(member);
    }

    /**
     * Otra persona responsable del equipo, la que lleve mas tiempo en el. Solo se
     * llama al irse quien lo creo; si no hubiera ninguna, el equipo se quedaria sin
     * dueno y sin nadie que pueda administrarlo.
     */
    private User otroResponsable(Team team, Long saliente) {
        return team.getMembers().stream()
                .filter(member -> member.getRole() == Role.RESPONSABLE)
                .filter(member -> !member.getUser().getId().equals(saliente))
                .min(java.util.Comparator.comparing(TeamMember::getId))
                .map(TeamMember::getUser)
                .orElseThrow(() -> new BusinessRuleException(
                        "Eres la unica persona responsable: nombra a otra antes de salirte del equipo"));
    }

    public Team getTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo", teamId));
    }

    private TeamMember getMember(Long teamId, Long userId) {
        return teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El usuario %d no pertenece al equipo %d".formatted(userId, teamId)));
    }

    private void ensureNotLastResponsable(Long teamId) {
        if (teamMemberRepository.countByTeam_IdAndRole(teamId, Role.RESPONSABLE) <= 1) {
            throw new BusinessRuleException(
                    "El equipo se quedaria sin responsable: nombra antes a otra persona responsable");
        }
    }

    private Role roleOf(Team team, Long userId) {
        return team.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(userId))
                .map(TeamMember::getRole)
                .findFirst()
                .orElse(null);
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessRuleException("No hay usuario autenticado"));
    }
}
