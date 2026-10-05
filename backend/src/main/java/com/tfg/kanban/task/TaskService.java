package com.tfg.kanban.task;

import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.board.BoardService;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.TaskRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.domain.spec.TaskSpecifications;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.task.dto.MoveTaskRequest;
import com.tfg.kanban.task.dto.TaskRequest;
import com.tfg.kanban.task.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tareas del tablero.
 *
 * El orden dentro de cada columna se guarda en {@code position} y se reindexa
 * (0..n-1) despues de cada alta, baja o movimiento, de forma que el drag &amp; drop
 * del frontend siempre recibe posiciones consecutivas y sin huecos.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final BoardService boardService;
    private final ActivityService activityService;
    private final TaskMapper taskMapper;

    public PageResponse<TaskResponse> search(Long projectId, Long boardId, Long columnId,
                                             Long assigneeId, TaskPriority priority, Pageable pageable) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return PageResponse.of(
                taskRepository.findAll(
                        TaskSpecifications.search(userId, projectId, boardId, columnId, assigneeId, priority),
                        pageable),
                taskMapper::toResponse);
    }

    public TaskResponse findById(Long taskId) {
        return taskMapper.toResponse(getTask(taskId));
    }

    @Transactional
    public TaskResponse create(Long columnId, TaskRequest request) {
        BoardColumn column = boardService.getColumn(columnId);
        // Cuantas hay ahora: la nueva entra la ultima, en esa posicion.
        long tasksInColumn = taskRepository.countByBoardColumn_Id(columnId);

        Long userId = SecurityUtils.requireCurrentUserId();
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));

        Task task = Task.builder()
                .title(request.title().trim())
                .description(request.description())
                .type(request.type() == null ? TaskType.TAREA : request.type())
                .priority(request.priority() == null ? TaskPriority.MEDIA : request.priority())
                .dueDate(request.dueDate())
                .estimatedHours(request.estimatedHours())
                .actualHours(request.actualHours())
                .boardColumn(column)
                .assignees(resolveAssignees(column, request.assigneeIds()))
                .createdBy(creator)
                .position((int) tasksInColumn)
                .build();

        Task saved = taskRepository.save(task);
        activityService.record(projectOf(column), ActivityAction.CREACION, "Task", saved.getId(),
                "Creó la tarea '%s' en '%s'".formatted(saved.getTitle(), column.getName()));

        return taskMapper.toResponse(saved);
    }

    @Transactional
    public TaskResponse update(Long taskId, TaskRequest request) {
        Task task = getTask(taskId);
        Set<Long> previousIds = task.getAssignees().stream().map(User::getId).collect(Collectors.toSet());

        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setType(request.type() == null ? TaskType.TAREA : request.type());
        task.setPriority(request.priority() == null ? TaskPriority.MEDIA : request.priority());
        task.setDueDate(request.dueDate());
        task.setEstimatedHours(request.estimatedHours());
        task.setActualHours(request.actualHours());
        // Se muta la coleccion gestionada por Hibernate, no se reasigna: asi el
        // @ManyToMany refleja el cambio sobre la misma referencia que observa.
        task.getAssignees().clear();
        task.getAssignees().addAll(resolveAssignees(task.getBoardColumn(), request.assigneeIds()));

        Project project = projectOf(task.getBoardColumn());
        activityService.record(project, ActivityAction.ACTUALIZACION, "Task", task.getId(),
                "Actualizó la tarea '%s'".formatted(task.getTitle()));

        Set<Long> newIds = task.getAssignees().stream().map(User::getId).collect(Collectors.toSet());
        if (!previousIds.equals(newIds)) {
            activityService.record(project, ActivityAction.ASIGNACION, "Task", task.getId(),
                    assignmentMessage(task));
        }

        return taskMapper.toResponse(task);
    }

    /** Mueve una tarea a otra columna y/o posicion (drag &amp; drop). */
    @Transactional
    public TaskResponse move(Long taskId, MoveTaskRequest request) {
        Task task = getTask(taskId);
        BoardColumn origin = task.getBoardColumn();
        BoardColumn target = boardService.getColumn(request.columnId());

        if (!origin.getBoard().getId().equals(target.getBoard().getId())) {
            throw new BusinessRuleException("No se puede mover una tarea a un tablero distinto");
        }

        boolean changesColumn = !origin.getId().equals(target.getId());

        List<Task> targetTasks = new ArrayList<>(
                taskRepository.findByBoardColumn_IdOrderByPositionAsc(target.getId()));
        targetTasks.removeIf(candidate -> candidate.getId().equals(taskId));

        int position = Math.min(Math.max(request.position(), 0), targetTasks.size());
        task.setBoardColumn(target);
        targetTasks.add(position, task);
        reindex(targetTasks);

        if (changesColumn) {
            reindex(taskRepository.findByBoardColumn_IdOrderByPositionAsc(origin.getId()).stream()
                    .filter(candidate -> !candidate.getId().equals(taskId))
                    .toList());

            activityService.record(projectOf(target), ActivityAction.MOVIMIENTO, "Task", task.getId(),
                    "Movió la tarea '%s' de '%s' a '%s'"
                            .formatted(task.getTitle(), origin.getName(), target.getName()));
        } else {
            activityService.record(projectOf(target), ActivityAction.MOVIMIENTO, "Task", task.getId(),
                    "Reordenó la tarea '%s' dentro de '%s'".formatted(task.getTitle(), target.getName()));
        }

        return taskMapper.toResponse(task);
    }

    @Transactional
    public void delete(Long taskId) {
        Task task = getTask(taskId);
        BoardColumn column = task.getBoardColumn();

        activityService.record(projectOf(column), ActivityAction.BORRADO, "Task", task.getId(),
                "Borró la tarea '%s'".formatted(task.getTitle()));

        taskRepository.delete(task);
        taskRepository.flush();
        reindex(taskRepository.findByBoardColumn_IdOrderByPositionAsc(column.getId()));
    }

    public Task getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea", taskId));
    }

    /** Solo se puede asignar una tarea a gente que pertenezca al equipo del proyecto. */
    private Set<User> resolveAssignees(BoardColumn column, List<Long> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Long teamId = projectOf(column).getTeam().getId();
        Set<Long> unicos = new LinkedHashSet<>(assigneeIds);
        Set<User> resueltos = new LinkedHashSet<>();
        for (Long id : unicos) {
            if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, id)) {
                throw new BusinessRuleException("El usuario asignado no pertenece al equipo del proyecto");
            }
            resueltos.add(userRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario", id)));
        }
        return resueltos;
    }

    /** El mensaje de actividad segun cuantos responsables quedan tras el cambio. */
    private String assignmentMessage(Task task) {
        List<String> nombres = task.getAssignees().stream().map(User::getFullName).toList();
        if (nombres.isEmpty()) {
            return "Dejó sin asignar la tarea '%s'".formatted(task.getTitle());
        }
        return "Asignó la tarea '%s' a %s".formatted(task.getTitle(), String.join(", ", nombres));
    }

    private Project projectOf(BoardColumn column) {
        return column.getBoard().getProject();
    }

    private void reindex(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setPosition(i);
        }
    }

}
