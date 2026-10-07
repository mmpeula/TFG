package com.tfg.kanban.workspace;

import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.board.DefaultBoard;
import com.tfg.kanban.common.CountByEntity;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.*;
import com.tfg.kanban.domain.spec.ProjectSpecifications;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.storage.FileStorageService;
import com.tfg.kanban.workspace.dto.CreateWorkspaceRequest;
import com.tfg.kanban.workspace.dto.UpdateWorkspaceRequest;
import com.tfg.kanban.workspace.dto.WorkspaceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final InvitationRepository invitationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final FileStorageService fileStorageService;

    /**
     * Todos los workspaces del usuario, con los numeros que se ven en la tarjeta.
     *
     * Es la pantalla de inicio, la que mas se carga, asi que se resuelve con cinco
     * consultas fijas: los proyectos, sus columnas, las tareas de esas columnas,
     * los miembros de cada equipo y el ultimo movimiento. Ninguna depende de
     * cuantos workspaces tenga la persona.
     */
    public List<WorkspaceResponse> findMine() {
        Long userId = SecurityUtils.requireCurrentUserId();
        List<Project> projects = projectRepository.findAll(
                ProjectSpecifications.visibleTo(userId),
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
        Long userId = SecurityUtils.requireCurrentUserId();
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
        // Un docente crea espacios como cualquier alumno (y es su RESPONSABLE); lo que no puede
        // hacer es un administrador, que no pertenece a ningun equipo.
        if (owner.getRole() == Role.ADMIN) {
            throw new BusinessRuleException("Una cuenta de administrador no puede crear espacios de trabajo");
        }
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

        project.getBoards().add(DefaultBoard.of("Tablero", null, project));
        team.getProjects().add(project);

        teamRepository.save(team);
        Project saved = projectRepository.save(project);

        activityService.record(saved, ActivityAction.CREACION, "Project", saved.getId(),
                "Creó el espacio de trabajo '%s'".formatted(saved.getName()));

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
                "Actualizó el espacio de trabajo '%s'".formatted(project.getName()));

        return findById(workspaceId);
    }

    /**
     * Borra el workspace con su tablero, sus tareas y su foto, y tambien su equipo.
     *
     * <p>El equipo existe solo para su workspace (se crean juntos), asi que se va con
     * el lo borre quien lo borre: el responsable que lo creo, otro responsable o
     * soporte. Si se quedara, sus miembros e invitaciones seguirian en la base de
     * datos sin ninguna pantalla desde la que alcanzarlos.</p>
     */
    @Transactional
    public void delete(Long workspaceId) {
        Project project = getProject(workspaceId);
        Team team = project.getTeam();
        String cover = project.getCoverStoredName();

        projectRepository.delete(project);
        projectRepository.flush();

        if (projectRepository.findByTeam_Id(team.getId()).isEmpty()) {
            // Las invitaciones, aunque ya esten respondidas, apuntan al equipo: sin quitarlas antes,
            // ningun espacio al que se haya invitado a alguien se podria borrar.
            invitationRepository.deleteByTeam_Id(team.getId());
            teamRepository.delete(team);
        }
        if (cover != null) {
            fileStorageService.deleteAfterCommit(cover);
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
        String coverUrl = project.getCoverStoredName() == null ? null
                : "/api/workspaces/%d/photo".formatted(project.getId());

        return new WorkspaceResponse(
                project.getId(), project.getName(), project.getDescription(),
                project.getColor(), project.getIcon(),
                project.getTeam().getId(), boardId, role,
                total, done, total - done,
                total == 0 ? 0 : Math.round(done * 100f / total),
                memberCount, project.getStartDate(), project.getDeadline(), project.getSprintDays(), last,
                project.isEnabled(), coverUrl);
    }

    /** Sustituye la foto del workspace, borrando la anterior si habia una. */
    @Transactional
    public WorkspaceResponse uploadCover(Long workspaceId, MultipartFile file) {
        Project project = getProject(workspaceId);
        FileStorageService.StoredImage image = fileStorageService.storeImage(file);
        String previous = project.getCoverStoredName();
        project.setCoverStoredName(image.storedName());
        project.setCoverContentType(image.contentType());
        if (previous != null) {
            fileStorageService.deleteAfterCommit(previous);
        }
        return findById(workspaceId);
    }

    /** Quita la foto del workspace; vuelve a mostrarse con su color e icono. */
    @Transactional
    public void removeCover(Long workspaceId) {
        Project project = getProject(workspaceId);
        String previous = project.getCoverStoredName();
        if (previous == null) {
            return;
        }
        project.setCoverStoredName(null);
        project.setCoverContentType(null);
        fileStorageService.deleteAfterCommit(previous);
    }

    public CoverFile loadCover(Long workspaceId) {
        Project project = getProject(workspaceId);
        if (project.getCoverStoredName() == null) {
            throw new ResourceNotFoundException("Este espacio de trabajo no tiene foto");
        }
        Resource resource = fileStorageService.loadAsResource(project.getCoverStoredName());
        return new CoverFile(resource, project.getCoverContentType());
    }

    /** Contenido de la foto del workspace junto con su tipo MIME, en una sola lectura del proyecto. */
    public record CoverFile(Resource resource, String contentType) {
    }

    public Project getProject(Long workspaceId) {
        return projectRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Espacio de trabajo", workspaceId));
    }

    private Role roleIn(Project project) {
        return teamMemberRepository.findByTeam_IdAndUser_Id(project.getTeam().getId(), SecurityUtils.requireCurrentUserId())
                .map(TeamMember::getRole).orElse(null);
    }

}
