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

        TeamMember member = TeamMember.builder()
                .team(team)
                .user(user)
                .role(ensureTeamRole(request.role() == null ? Role.MIEMBRO : request.role()))
                .build();

        return TeamMemberResponse.from(teamMemberRepository.save(member));
    }

    @Transactional
    public TeamMemberResponse updateMemberRole(Long teamId, Long userId, UpdateMemberRoleRequest request) {
        TeamMember member = getMember(teamId, userId);
        Role role = ensureTeamRole(request.role());

        if (member.getRole() == Role.RESPONSABLE && role != Role.RESPONSABLE) {
            ensureNotLastResponsable(teamId);
        }
        member.setRole(role);
        return TeamMemberResponse.from(member);
    }

    /**
     * ADMIN es un rol global (administra la plataforma, no un equipo): no tiene
     * sentido como rol de {@link TeamMember} y aqui se rechaza explicitamente,
     * aunque el DTO de entrada acepte cualquier valor del enum {@link Role}.
     */
    private Role ensureTeamRole(Role role) {
        if (role == Role.ADMIN) {
            throw new BusinessRuleException("ADMIN es un rol de plataforma, no se puede asignar en un equipo");
        }
        return role;
    }

    @Transactional
    public void removeMember(Long teamId, Long userId) {
        TeamMember member = getMember(teamId, userId);
        Team team = member.getTeam();

        if (team.getOwner().getId().equals(userId)) {
            throw new BusinessRuleException("No se puede expulsar al creador del equipo");
        }
        if (member.getRole() == Role.RESPONSABLE) {
            ensureNotLastResponsable(teamId);
        }
        teamMemberRepository.delete(member);
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
            throw new BusinessRuleException("El equipo debe tener al menos un RESPONSABLE");
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
