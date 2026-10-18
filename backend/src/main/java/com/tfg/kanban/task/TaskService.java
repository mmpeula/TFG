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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tareas del tablero.
 *
 * El orden dentro de cada columna se guarda en {@code position} y se reindexa
 * (0..n-1) despues de cada baja o movimiento, de forma que el drag &amp; drop
 * del frontend siempre recibe posiciones consecutivas y sin huecos. Las altas
 * entran al final, asi que no desplazan a nadie.
 *
 * Una tarea esta terminada cuando esta en la ultima columna ({@link BoardColumn#isDone()}),
 * y {@code completedAt} guarda desde cuando: se pone al entrar ahi y se quita al salir.
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
                // Creada ya en «Hecho» (por ejemplo, algo que se hizo y se apunta despues): terminada hoy.
                .completedAt(column.isDone() ? Instant.now() : null)
                .assignees(resolveAssignees(column, request.assigneeIds(), Set.of()))
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
        task.getAssignees().addAll(resolveAssignees(task.getBoardColumn(), request.assigneeIds(), previousIds));

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
        reindex(targetTasks, task);

        if (changesColumn) {
            // Entrar en la ultima columna es terminarla, hoy; salir de ella la vuelve a abrir.
            task.setCompletedAt(target.isDone() ? Instant.now() : null);
            if (origin.isDone() && !target.isDone()) {
                dropFormerMembers(task, target);
            }
            reindex(taskRepository.findByBoardColumn_IdOrderByPositionAsc(origin.getId()).stream()
                    .filter(candidate -> !candidate.getId().equals(taskId))
                    .toList(), null);

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
        reindex(taskRepository.findByBoardColumn_IdOrderByPositionAsc(column.getId()), null);
    }

    /**
     * Suelta las tareas sin terminar que tenia asignadas alguien que se va del equipo.
     *
     * <p>Si siguiera en ellas, nadie veria que se han quedado sin hacer: no saldrian
     * como «sin asignar» ni en el resumen de nadie del equipo. Las terminadas se
     * quedan como estan, para que conste quien las hizo.</p>
     */
    @Transactional
    public void releaseOpenTasks(Long teamId, User leaving) {
        release(taskRepository.findOpenByTeamAndAssignee(teamId, leaving.getId()), leaving,
                "%s ha salido del equipo y deja la tarea '%s'");
    }

    /**
     * Lo mismo al dar de baja una cuenta, en todos sus equipos. La baja no la saca de
     * ellos (se puede reactivar), pero mientras dure no puede hacer nada.
     */
    @Transactional
    public void releaseOpenTasksOf(User disabled) {
        release(taskRepository.findOpenByAssignee(disabled.getId()), disabled,
                "La cuenta de %s se ha dado de baja y deja la tarea '%s'");
    }

    private void release(List<Task> tasks, User user, String message) {
        for (Task task : tasks) {
            task.getAssignees().removeIf(assignee -> assignee.getId().equals(user.getId()));
            activityService.record(projectOf(task.getBoardColumn()), ActivityAction.ASIGNACION, "Task", task.getId(),
                    message.formatted(user.getFullName(), task.getTitle())
                            + (task.getAssignees().isEmpty() ? " sin asignar" : ""));
        }
    }

    public Task getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea", taskId));
    }

    /**
     * Solo se puede asignar una tarea a gente del equipo del proyecto con la cuenta activa.
     *
     * <p>La excepcion es quien ya estaba en ella y se ha ido del equipo o se ha dado de
     * baja: en una tarea terminada se queda, para que conste quien la hizo y se pueda
     * seguir editando; en una sin terminar se quita sin dar error, porque ya no puede hacerla.</p>
     */
    private Set<User> resolveAssignees(BoardColumn column, List<Long> assigneeIds, Set<Long> alreadyAssigned) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Long teamId = projectOf(column).getTeam().getId();
        Set<Long> unicos = new LinkedHashSet<>(assigneeIds);
        Set<User> resueltos = new LinkedHashSet<>();
        for (Long id : unicos) {
            boolean member = teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, id);
            if (!member && !alreadyAssigned.contains(id)) {
                throw new BusinessRuleException("El usuario asignado no pertenece al equipo del proyecto");
            }
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
            if (!member || !user.isEnabled()) {
                if (!alreadyAssigned.contains(id)) {
                    throw new BusinessRuleException(
                            "La cuenta de %s está dada de baja: no se le pueden asignar tareas".formatted(user.getFullName()));
                }
                if (!column.isDone()) {
                    continue;
                }
            }
            resueltos.add(user);
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

    /**
     * Al reabrir una tarea terminada, quien ya no esta en el equipo o tiene la cuenta
     * dada de baja deja de estar asignado a ella.
     */
    private void dropFormerMembers(Task task, BoardColumn column) {
        Long teamId = projectOf(column).getTeam().getId();
        for (User user : List.copyOf(task.getAssignees())) {
            String motivo = !teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, user.getId())
                    ? "ya no está en el equipo"
                    : !user.isEnabled() ? "su cuenta está dada de baja" : null;
            if (motivo != null) {
                task.getAssignees().remove(user);
                activityService.record(projectOf(column), ActivityAction.ASIGNACION, "Task", task.getId(),
                        "Quitó a %s de la tarea '%s' al reabrirla: %s"
                                .formatted(user.getFullName(), task.getTitle(), motivo));
            }
        }
    }

    private Project projectOf(BoardColumn column) {
        return column.getBoard().getProject();
    }

    /**
     * Deja la columna con posiciones 0..n-1, sin huecos.
     *
     * <p>Solo la tarea que se mueve ({@code moved}, si la hay) se cambia como entidad:
     * su ultima modificacion es ahora. Las demas solo se desplazan, asi que se
     * recolocan sin tocar su {@code updatedAt} (ver {@link TaskRepository#updatePosition}),
     * y solo las que de verdad cambian de sitio.</p>
     */
    private void reindex(List<Task> tasks, Task moved) {
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            if (task.equals(moved)) {
                task.setPosition(i);
            } else if (task.getPosition() == null || task.getPosition() != i) {
                taskRepository.updatePosition(task.getId(), i);
            }
        }
    }

}
