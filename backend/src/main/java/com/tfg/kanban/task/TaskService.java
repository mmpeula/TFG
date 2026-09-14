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
import java.util.List;

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
        Long userId = currentUserId();
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
        checkWipLimit(column, taskRepository.countByBoardColumn_Id(columnId) + 1);

        User creator = userRepository.findById(currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", currentUserId()));

        Task task = Task.builder()
                .title(request.title().trim())
                .description(request.description())
                .type(request.type() == null ? TaskType.TAREA : request.type())
                .priority(request.priority() == null ? TaskPriority.MEDIA : request.priority())
                .dueDate(request.dueDate())
                .estimatedHours(request.estimatedHours())
                .boardColumn(column)
                .assignee(resolveAssignee(column, request.assigneeId()))
                .createdBy(creator)
                .position((int) taskRepository.countByBoardColumn_Id(columnId))
                .build();

        Task saved = taskRepository.save(task);
        activityService.record(projectOf(column), ActivityAction.CREACION, "Task", saved.getId(),
                "Creo la tarea '%s' en '%s'".formatted(saved.getTitle(), column.getName()));

        return taskMapper.toResponse(saved);
    }

    @Transactional
    public TaskResponse update(Long taskId, TaskRequest request) {
        Task task = getTask(taskId);
        User previousAssignee = task.getAssignee();

        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setType(request.type() == null ? TaskType.TAREA : request.type());
        task.setPriority(request.priority() == null ? TaskPriority.MEDIA : request.priority());
        task.setDueDate(request.dueDate());
        task.setEstimatedHours(request.estimatedHours());
        task.setAssignee(resolveAssignee(task.getBoardColumn(), request.assigneeId()));

        Project project = projectOf(task.getBoardColumn());
        activityService.record(project, ActivityAction.ACTUALIZACION, "Task", task.getId(),
                "Actualizo la tarea '%s'".formatted(task.getTitle()));

        if (!sameUser(previousAssignee, task.getAssignee())) {
            activityService.record(project, ActivityAction.ASIGNACION, "Task", task.getId(),
                    task.getAssignee() == null
                            ? "Dejo sin asignar la tarea '%s'".formatted(task.getTitle())
                            : "Asigno la tarea '%s' a %s".formatted(task.getTitle(),
                                    task.getAssignee().getFullName()));
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
        if (changesColumn) {
            checkWipLimit(target, taskRepository.countByBoardColumn_Id(target.getId()) + 1);
        }

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
                    "Movio la tarea '%s' de '%s' a '%s'"
                            .formatted(task.getTitle(), origin.getName(), target.getName()));
        } else {
            activityService.record(projectOf(target), ActivityAction.MOVIMIENTO, "Task", task.getId(),
                    "Reordeno la tarea '%s' dentro de '%s'".formatted(task.getTitle(), target.getName()));
        }

        return taskMapper.toResponse(task);
    }

    @Transactional
    public void delete(Long taskId) {
        Task task = getTask(taskId);
        BoardColumn column = task.getBoardColumn();

        activityService.record(projectOf(column), ActivityAction.BORRADO, "Task", task.getId(),
                "Borro la tarea '%s'".formatted(task.getTitle()));

        taskRepository.delete(task);
        taskRepository.flush();
        reindex(taskRepository.findByBoardColumn_IdOrderByPositionAsc(column.getId()));
    }

    public Task getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea", taskId));
    }

    private void checkWipLimit(BoardColumn column, long tasksAfterChange) {
        if (column.getWipLimit() != null && tasksAfterChange > column.getWipLimit()) {
            throw new BusinessRuleException(
                    "La columna '%s' ha alcanzado su limite WIP (%d)"
                            .formatted(column.getName(), column.getWipLimit()));
        }
    }

    /** Solo se puede asignar una tarea a alguien que pertenezca al equipo del proyecto. */
    private User resolveAssignee(BoardColumn column, Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        Long teamId = projectOf(column).getTeam().getId();
        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, assigneeId)) {
            throw new BusinessRuleException("El usuario asignado no pertenece al equipo del proyecto");
        }
        return userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", assigneeId));
    }

    private Project projectOf(BoardColumn column) {
        return column.getBoard().getProject();
    }

    private void reindex(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setPosition(i);
        }
    }

    private boolean sameUser(User a, User b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.getId().equals(b.getId());
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessRuleException("No hay usuario autenticado"));
    }
}
