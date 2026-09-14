package com.tfg.kanban.workspace;

import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.common.CountByEntity;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.*;
import com.tfg.kanban.domain.spec.ProjectSpecifications;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.workspace.dto.CreateWorkspaceRequest;
import com.tfg.kanban.workspace.dto.UpdateWorkspaceRequest;
import com.tfg.kanban.workspace.dto.WorkspaceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Workspaces: la unidad con la que trabaja el estudiante.
 *
 * <p>Un workspace es un <b>proyecto</b> con su equipo y su tablero. Los tres se
 * crean juntos en una sola operacion porque exponer esa jerarquia en la interfaz
 * obligaba a entender el modelo de datos antes de poder escribir la primera tarea.
 * Por debajo no cambia nada: el resto de la API sigue trabajando con equipos,
 * proyectos y tableros.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    /** Columnas con las que nace todo workspace. */
    private static final List<String> DEFAULT_COLUMNS = List.of("Por hacer", "En progreso", "Hecho");

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;

    /**
     * Todos los workspaces del usuario, con los numeros que se ven en la tarjeta.
     *
     * Es la pantalla de inicio, la que mas se carga, asi que se resuelve con cinco
     * consultas fijas: los proyectos, sus columnas, las tareas de esas columnas,
     * los miembros de cada equipo y el ultimo movimiento. Ninguna depende de
     * cuantos workspaces tenga la persona.
     */
    public List<WorkspaceResponse> findMine() {
        Long userId = currentUserId();
        List<Project> projects = projectRepository.findAll(
                ProjectSpecifications.visibleTo(userId, null, null),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        if (projects.isEmpty()) {
            return List.of();
        }

        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        List<Long> teamIds = projects.stream().map(project -> project.getTeam().getId()).distinct().toList();

        // (proyecto, tablero, columna, posicion) para todos los proyectos de golpe.
        Map<Long, List<ColumnRow>> columnsByProject = new LinkedHashMap<>();
        for (Object[] row : columnRepository.findColumnRowsByProjectIds(projectIds)) {
            columnsByProject
                    .computeIfAbsent((Long) row[0], key -> new ArrayList<>())
                    .add(new ColumnRow((Long) row[1], (Long) row[2]));
        }

        List<Long> columnIds = columnsByProject.values().stream()
                .flatMap(List::stream).map(ColumnRow::columnId).toList();
        Map<Long, Long> tasksByColumn = columnIds.isEmpty() ? Map.of()
                : taskRepository.countByColumnIds(columnIds).stream()
                        .collect(Collectors.toMap(CountByEntity::entityId, CountByEntity::total));

        Map<Long, Long> membersByTeam = teamMemberRepository.countByTeamIds(teamIds).stream()
                .collect(Collectors.toMap(CountByEntity::entityId, CountByEntity::total));
        Map<Long, Role> rolesByTeam = teamMemberRepository.rolesInTeams(teamIds, userId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Role) row[1]));
        Map<Long, Instant> lastActivity = activityLogRepository.lastActivityByProjects(projectIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Instant) row[1]));

        return projects.stream()
                .map(project -> {
                    List<ColumnRow> columns = columnsByProject.getOrDefault(project.getId(), List.of());
                    Long teamId = project.getTeam().getId();
                    return card(project,
                            columns.isEmpty() ? null : columns.get(0).boardId(),
                            columns.stream().map(ColumnRow::columnId).toList(),
                            tasksByColumn,
                            membersByTeam.getOrDefault(teamId, 0L).intValue(),
                            rolesByTeam.get(teamId),
                            lastActivity.get(project.getId()));
                })
                .toList();
    }

    /** Una columna del tablero, con el tablero al que pertenece. */
    private record ColumnRow(Long boardId, Long columnId) {
    }

    public WorkspaceResponse findById(Long workspaceId) {
        Project project = getProject(workspaceId);
        List<BoardColumn> columns = columnRepository.findByProjectIds(List.of(workspaceId));
        List<Long> columnIds = columns.stream().map(BoardColumn::getId).toList();
        Map<Long, Long> tasks = columnIds.isEmpty() ? Map.of()
                : taskRepository.countByColumnIds(columnIds).stream()
                        .collect(Collectors.toMap(CountByEntity::entityId, CountByEntity::total));
        Instant last = activityLogRepository.findTop1ByProject_IdOrderByOccurredAtDesc(workspaceId).stream()
                .findFirst().map(ActivityLog::getOccurredAt).orElse(null);

        return card(project,
                project.getBoards().isEmpty() ? null : project.getBoards().get(0).getId(),
                columnIds, tasks,
                project.getTeam().getMembers().size(),
                roleIn(project), last);
    }

    /**
     * Crea el workspace entero: equipo, proyecto, tablero y sus tres columnas.
     * Todo en una transaccion, para que no pueda quedar un equipo sin proyecto si
     * algo falla por el camino.
     */
    @Transactional
    public WorkspaceResponse create(CreateWorkspaceRequest request) {
        User owner = userRepository.findById(currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", currentUserId()));
        String name = request.name().trim();

        Team team = Team.builder()
                .name(name)
                .description(request.description())
                .owner(owner)
                .build();
        team.getMembers().add(TeamMember.builder()
                .team(team)
                .user(owner)
                .role(Role.RESPONSABLE)
                .build());

        Project project = Project.builder()
                .name(name)
                .description(request.description())
                .color(request.color())
                .icon(request.icon())
                .sprintDays(request.sprintDays())
                .status(ProjectStatus.EN_CURSO)
                .team(team)
                .build();

        Board board = Board.builder()
                .name("Tablero")
                .project(project)
                .build();
        for (int i = 0; i < DEFAULT_COLUMNS.size(); i++) {
            board.getColumns().add(BoardColumn.builder()
                    .board(board)
                    .name(DEFAULT_COLUMNS.get(i))
                    .position(i)
                    .build());
        }
        project.getBoards().add(board);
        team.getProjects().add(project);

        teamRepository.save(team);
        Project saved = projectRepository.save(project);

        activityService.record(saved, ActivityAction.CREACION, "Project", saved.getId(),
                "Creo el workspace '%s'".formatted(saved.getName()));

        return findById(saved.getId());
    }

    @Transactional
    public WorkspaceResponse update(Long workspaceId, UpdateWorkspaceRequest request) {
        Project project = getProject(workspaceId);
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setColor(request.color());
        project.setIcon(request.icon());
        project.setStartDate(request.startDate());
        project.setDeadline(request.deadline());
        project.setSprintDays(request.sprintDays());

        // El equipo se llama igual que el workspace: al renombrarlo, se renombra.
        Team team = project.getTeam();
        if (team.getProjects().size() <= 1) {
            team.setName(project.getName());
        }

        activityService.record(project, ActivityAction.ACTUALIZACION, "Project", project.getId(),
                "Actualizo el workspace '%s'".formatted(project.getName()));

        return findById(workspaceId);
    }

    /**
     * Borra el workspace. Si su equipo se queda sin proyectos y lo creo esta misma
     * persona, se borra tambien: de otro modo quedarian equipos vacios invisibles.
     */
    @Transactional
    public void delete(Long workspaceId) {
        Project project = getProject(workspaceId);
        Team team = project.getTeam();

        projectRepository.delete(project);
        projectRepository.flush();

        boolean vacio = projectRepository.findByTeam_Id(team.getId()).isEmpty();
        boolean esMio = team.getOwner() != null && team.getOwner().getId().equals(currentUserId());
        if (vacio && esMio) {
            teamRepository.delete(team);
        }
    }

    /**
     * Arma la tarjeta. "Hecho" es la ultima columna del tablero, se llame como se
     * llame: asi el progreso sigue siendo correcto si el usuario las renombra.
     */
    private WorkspaceResponse card(Project project, Long boardId, List<Long> columnIds,
                                   Map<Long, Long> tasksByColumn, int memberCount,
                                   Role role, Instant lastActivity) {
        int total = 0;
        int done = 0;
        Long doneColumnId = columnIds.isEmpty() ? null : columnIds.get(columnIds.size() - 1);
        for (Long columnId : columnIds) {
            int count = tasksByColumn.getOrDefault(columnId, 0L).intValue();
            total += count;
            if (columnId.equals(doneColumnId)) {
                done += count;
            }
        }

        Instant last = lastActivity != null ? lastActivity
                : project.getUpdatedAt() != null ? project.getUpdatedAt() : project.getCreatedAt();

        return new WorkspaceResponse(
                project.getId(), project.getName(), project.getDescription(),
                project.getColor(), project.getIcon(),
                project.getTeam().getId(), boardId, role,
                total, done, total - done,
                total == 0 ? 0 : Math.round(done * 100f / total),
                memberCount, project.getStartDate(), project.getDeadline(), project.getSprintDays(), last);
    }

    public Project getProject(Long workspaceId) {
        return projectRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }

    private Role roleIn(Project project) {
        return teamMemberRepository.findByTeam_IdAndUser_Id(project.getTeam().getId(), currentUserId())
                .map(TeamMember::getRole).orElse(null);
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId().orElseThrow();
    }
}
