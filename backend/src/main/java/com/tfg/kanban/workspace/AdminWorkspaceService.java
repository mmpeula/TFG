package com.tfg.kanban.workspace;

import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.CountByEntity;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.*;
import com.tfg.kanban.team.TeamService;
import com.tfg.kanban.team.dto.UpdateMemberRoleRequest;
import com.tfg.kanban.workspace.dto.AdminWorkspaceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Los workspaces de toda la plataforma, para dar soporte.
 *
 * <p>Es lo unico que ve un administrador de un workspace ajeno, y a proposito no
 * es todo: hay datos de diagnostico (quien esta, cuantas tareas, cuando se movio)
 * pero no se expone ni una tarea, ni un comentario, ni un fichero. Administrar
 * cuentas no da derecho a leer el trabajo de un grupo.</p>
 *
 * <p>Por lo mismo, las acciones existen para <b>desbloquear o parar</b>, no para
 * participar: cambiar el rol de quien ya esta en el equipo, suspender el workspace
 * y reactivarlo, y borrarlo. Un administrador no puede meter a nadie en un equipo
 * —eso seria una puerta trasera a cualquier grupo—, ni escribir en el tablero.</p>
 *
 * <p>Suspender esta antes que borrar a proposito: casi siempre lo que hace falta es
 * parar un workspace un tiempo, y borrarlo se lleva por delante el trabajo de un
 * grupo entero sin vuelta atras.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminWorkspaceService {

    private final ProjectRepository projectRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ActivityService activityService;
    private final TeamService teamService;
    private final WorkspaceService workspaceService;

    /**
     * Todos los workspaces. Se resuelve con consultas por lotes, como la pantalla
     * de inicio: ninguna depende de cuantos workspaces haya en la plataforma.
     */
    public List<AdminWorkspaceResponse> findAll() {
        List<Project> projects = projectRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        if (projects.isEmpty()) {
            return List.of();
        }

        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        List<Long> teamIds = projects.stream().map(project -> project.getTeam().getId()).distinct().toList();

        Map<Long, List<AdminWorkspaceResponse.Member>> membersByTeam = new LinkedHashMap<>();
        for (TeamMember member : teamMemberRepository.findWithUserByTeamIds(teamIds)) {
            membersByTeam
                    .computeIfAbsent(member.getTeam().getId(), key -> new ArrayList<>())
                    .add(new AdminWorkspaceResponse.Member(
                            member.getUser().getId(),
                            member.getUser().getFullName(),
                            member.getUser().getEmail(),
                            member.getRole(),
                            member.getUser().isEnabled()));
        }

        Map<Long, Long> tasksByProject = tasksByProject(projectIds);
        Map<Long, Instant> lastActivity = activityLogRepository.lastActivityByProjects(projectIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Instant) row[1]));

        return projects.stream().map(project -> {
            Long teamId = project.getTeam().getId();
            List<AdminWorkspaceResponse.Member> members = membersByTeam.getOrDefault(teamId, List.of());
            return new AdminWorkspaceResponse(
                    project.getId(),
                    project.getName(),
                    teamId,
                    project.getTeam().getOwner() == null ? null : project.getTeam().getOwner().getFullName(),
                    tasksByProject.getOrDefault(project.getId(), 0L).intValue(),
                    project.isEnabled(),
                    sinResponsableActivo(members),
                    project.getCreatedAt(),
                    lastActivity.get(project.getId()),
                    members);
        }).toList();
    }

    /**
     * Cambia el rol que alguien tiene <b>dentro</b> del workspace.
     *
     * <p>Sirve sobre todo para el caso que deja un equipo muerto —la unica persona
     * responsable se fue o se le deshabilito la cuenta, y nadie puede administrarlo
     * desde dentro—: se nombra responsable a otro de sus miembros. Tambien vale para
     * degradar a alguien a alumno.</p>
     *
     * <p>Tres limites que no se saltan ni desde soporte. Ser docente se elige al
     * invitar y no se cambia despues, ni hacia el ni desde el (ver
     * {@code TeamRoles.ensureDocenteRoleUnchanged}). Solo se cambia el rol de
     * quien <b>ya pertenece</b> al equipo: si un administrador pudiera anadir a
     * cualquiera, tendria una puerta de entrada a todos los grupos. Y el equipo
     * nunca se queda sin responsable, porque sin nadie que pueda administrarlo el
     * workspace queda inservible; esa regla es la misma que rige dentro del equipo
     * y se reutiliza en lugar de repetirla.</p>
     */
    @Transactional
    public void cambiarRol(Long workspaceId, Long userId, Role role) {
        Project project = getProject(workspaceId);
        Long teamId = project.getTeam().getId();

        TeamMember member = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Esa persona no está en el equipo. Desde soporte solo se puede cambiar "
                                + "el rol de quien ya pertenece al espacio de trabajo."));

        if (!member.getUser().isEnabled()) {
            throw new BusinessRuleException(
                    "Esa cuenta está deshabilitada: reactívala antes de cambiarle el rol");
        }

        Role anterior = member.getRole();
        // Aqui viven las dos reglas que hay que respetar: que el rol encaje con el
        // tipo de cuenta y que no se quede el equipo sin ningun responsable.
        teamService.updateMemberRole(teamId, userId, new UpdateMemberRoleRequest(role));

        // Queda en la actividad del propio workspace: el equipo ve quien lo toco.
        activityService.record(project, ActivityAction.ACTUALIZACION, "TeamMember", userId,
                "Soporte cambió el rol de '%s' de %s a %s".formatted(
                        member.getUser().getFullName(), anterior.label(), role.label()));
        log.info("Soporte: {} pasa de {} a {} en el workspace {}",
                member.getUser().getEmail(), anterior, role, workspaceId);
    }

    /**
     * Suspende o reactiva el workspace.
     *
     * <p>Suspender no borra nada: el equipo deja de poder abrirlo y de poder tocar
     * su equipo, pero el tablero, las tareas y los ficheros siguen ahi, y reactivarlo
     * lo deja todo como estaba. Es lo que hay que hacer cuando un workspace tiene que
     * parar por un rato y borrarlo seria desproporcionado.</p>
     */
    @Transactional
    public void setEnabled(Long workspaceId, boolean enabled) {
        Project project = getProject(workspaceId);
        if (project.isEnabled() == enabled) {
            return;
        }
        project.setEnabled(enabled);

        // Se anota antes de que deje de estar accesible, para que el equipo lo vea
        // al volver: es la explicacion de por que se quedo parado.
        activityService.record(project, ActivityAction.ACTUALIZACION, "Project", workspaceId,
                enabled ? "Soporte reactivó el espacio de trabajo" : "Soporte suspendió el espacio de trabajo");
        log.info("Soporte: workspace {} {}", workspaceId, enabled ? "reactivado" : "suspendido");
    }

    /**
     * Borra un workspace ajeno. Es el mismo borrado que el del responsable (ver
     * {@link WorkspaceService#delete}), con su equipo y su foto: solo cambia quien
     * puede pedirlo.
     */
    @Transactional
    public void delete(Long workspaceId) {
        String nombre = getProject(workspaceId).getName();
        workspaceService.delete(workspaceId);
        log.info("Soporte: borrado el workspace {} ('{}')", workspaceId, nombre);
    }

    /** Cuantas tareas tiene cada proyecto, pasando por sus columnas. */
    private Map<Long, Long> tasksByProject(List<Long> projectIds) {
        Map<Long, Long> porProyecto = new LinkedHashMap<>();
        Map<Long, Long> projectByColumn = new LinkedHashMap<>();
        for (Object[] row : columnRepository.findColumnRowsByProjectIds(projectIds)) {
            projectByColumn.put((Long) row[2], (Long) row[0]);
        }
        if (projectByColumn.isEmpty()) {
            return porProyecto;
        }
        for (CountByEntity count : taskRepository.countByColumnIds(List.copyOf(projectByColumn.keySet()))) {
            Long projectId = projectByColumn.get(count.entityId());
            porProyecto.merge(projectId, count.total(), Long::sum);
        }
        return porProyecto;
    }

    /** Nadie con rol RESPONSABLE y la cuenta activa: el workspace esta bloqueado. */
    private boolean sinResponsableActivo(List<AdminWorkspaceResponse.Member> members) {
        return members.stream().noneMatch(m -> m.role() == Role.RESPONSABLE && m.enabled());
    }

    private Project getProject(Long workspaceId) {
        return projectRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Espacio de trabajo", workspaceId));
    }
}
