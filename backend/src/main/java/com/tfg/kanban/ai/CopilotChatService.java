package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.ChatRequest;
import com.tfg.kanban.ai.dto.ChatResponse;
import com.tfg.kanban.ai.dto.ChatTurn;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.CommentRepository;
import com.tfg.kanban.domain.repository.TaskRepository;
import com.tfg.kanban.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * El copiloto conversacional del workspace.
 *
 * <p>A diferencia de {@link AiPlanService}, que propone tareas para el tablero,
 * aqui el modelo solo <b>responde</b>: no escribe nada. Lo que hace util la
 * respuesta es el contexto, que se arma con el estado real del tablero (tareas,
 * columnas, fechas, personas) en lugar de dejar que el modelo se lo imagine.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotChatService {

    private static final String SYSTEM_PROMPT = """
            Eres el copiloto de PlanifIA, una aplicacion donde estudiantes universitarios
            organizan sus practicas y trabajos en grupo.

            Respondes SIEMPRE en espanol, en tono cercano y directo, como un companero de
            clase que se ha leido el tablero.

            Reglas:
            - Usa UNICAMENTE los datos del workspace que se te dan. No inventes tareas,
              personas ni fechas que no aparezcan.
            - Si te falta un dato para responder, dilo en una frase y sugiere que hacer.
            - Se breve: como mucho 150 palabras. Ve al grano.
            - Cuando enumeres tareas, usa una lista con guiones y cita el titulo exacto.
            - No uses tecnicismos de gestion de proyectos ni lenguaje de empresa.
            - No repitas la pregunta ni empieces con "Claro" o "Por supuesto".
            """;

    /** Tope de turnos anteriores que se reenvian: mas no cabe junto al contexto. */
    private static final int MAX_HISTORY = 6;

    /** Tope de tareas que se describen. Un tablero de practicas rara vez pasa de aqui. */
    private static final int MAX_TASKS = 60;

    private final AiProperties properties;
    private final OllamaClient ollamaClient;
    private final WorkspaceService workspaceService;
    private final TaskRepository taskRepository;
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public ChatResponse answer(Long workspaceId, ChatRequest request) {
        if (!properties.isEnabled()) {
            throw new AiUnavailableException("El modulo de IA esta desactivado en la configuracion del servidor");
        }

        Project project = workspaceService.getProject(workspaceId);
        List<Task> tasks = tasksOf(project);
        String context = buildContext(project, tasks);

        List<OllamaClient.Message> messages = new ArrayList<>();
        messages.add(new OllamaClient.Message("user", context));
        messages.add(new OllamaClient.Message("assistant",
                "Vale, ya tengo delante el tablero de este workspace. Que necesitas?"));
        for (ChatTurn turn : recent(request.historyOrEmpty())) {
            messages.add(new OllamaClient.Message(normalizeRole(turn.role()), turn.content()));
        }
        messages.add(new OllamaClient.Message("user", request.message()));

        String answer = ollamaClient.chatText(SYSTEM_PROMPT, messages).trim();
        if (answer.isBlank()) {
            throw new AiUnavailableException("El modelo no ha sabido que responder; prueba a reformular la pregunta");
        }

        log.info("Copiloto: respuesta para el workspace {} con {} tareas de contexto", workspaceId, tasks.size());
        return ChatResponse.of(answer, properties.getModel(), summary(project, tasks));
    }

    private List<Task> tasksOf(Project project) {
        if (project.getBoards().isEmpty()) return List.of();
        return taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(project.getBoards().get(0).getId());
    }

    /**
     * El estado del tablero en texto plano. Va como primer mensaje del usuario y no
     * como parte del prompt de sistema para que el modelo lo trate como datos.
     */
    private String buildContext(Project project, List<Task> tasks) {
        LocalDate hoy = LocalDate.now();
        StringBuilder sb = new StringBuilder();

        sb.append("Datos del workspace «").append(project.getName()).append("».\n");
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            sb.append("Descripcion: ").append(project.getDescription()).append("\n");
        }
        sb.append("Fecha de hoy: ").append(hoy).append("\n");
        if (project.getDeadline() != null) {
            long dias = ChronoUnit.DAYS.between(hoy, project.getDeadline());
            sb.append("Entrega: ").append(project.getDeadline())
                    .append(dias >= 0 ? " (quedan %d dias)".formatted(dias) : " (ya vencida)").append("\n");
        }

        sb.append("\nPersonas del equipo:\n");
        for (TeamMember member : project.getTeam().getMembers()) {
            long suyas = tasks.stream()
                    .filter(task -> task.getAssignee() != null
                            && task.getAssignee().getId().equals(member.getUser().getId()))
                    .filter(task -> !esUltimaColumna(task))
                    .count();
            sb.append("- ").append(member.getUser().getFullName())
                    .append(" (").append(member.getRole().name().toLowerCase()).append("): ")
                    .append(suyas).append(" tareas pendientes\n");
        }

        if (tasks.isEmpty()) {
            sb.append("\nEl tablero esta vacio: todavia no hay ninguna tarea.\n");
            return sb.toString();
        }

        sb.append("\nTareas del tablero (").append(tasks.size()).append(" en total):\n");
        for (Task task : tasks.stream().limit(MAX_TASKS).toList()) {
            sb.append("- «").append(task.getTitle()).append("»")
                    .append(" | estado: ").append(task.getBoardColumn().getName())
                    .append(" | prioridad: ").append(task.getPriority().name().toLowerCase());
            if (task.getAssignee() != null) {
                sb.append(" | responsable: ").append(task.getAssignee().getFullName());
            } else {
                sb.append(" | sin responsable");
            }
            if (task.getDueDate() != null) {
                long dias = ChronoUnit.DAYS.between(hoy, task.getDueDate());
                sb.append(" | fecha limite: ").append(task.getDueDate())
                        .append(dias < 0 ? " (ATRASADA)" : dias <= 3 ? " (urge)" : "");
            }
            if (task.getEstimatedHours() != null) {
                sb.append(" | horas estimadas: ").append(task.getEstimatedHours());
            }
            long comentarios = commentRepository.countByTask_Id(task.getId());
            if (comentarios > 0) {
                sb.append(" | ").append(comentarios).append(" comentarios");
            }
            sb.append("\n");
        }
        if (tasks.size() > MAX_TASKS) {
            sb.append("(y ").append(tasks.size() - MAX_TASKS).append(" tareas mas que no caben aqui)\n");
        }
        return sb.toString();
    }

    /** La ultima columna del tablero es la de terminadas, se llame como se llame. */
    private boolean esUltimaColumna(Task task) {
        List<BoardColumn> columns = task.getBoardColumn().getBoard().getColumns();
        return !columns.isEmpty()
                && columns.get(columns.size() - 1).getId().equals(task.getBoardColumn().getId());
    }

    private String summary(Project project, List<Task> tasks) {
        long pendientes = tasks.stream().filter(task -> !esUltimaColumna(task)).count();
        return "%d tareas (%d pendientes) · %d personas".formatted(
                tasks.size(), pendientes, project.getTeam().getMembers().size());
    }

    private List<ChatTurn> recent(List<ChatTurn> history) {
        return history.size() <= MAX_HISTORY ? history : history.subList(history.size() - MAX_HISTORY, history.size());
    }

    /** Cualquier cosa que no sea del asistente se trata como del usuario. */
    private String normalizeRole(String role) {
        return "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
    }
}
