package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.ChatRequest;
import com.tfg.kanban.ai.dto.ChatResponse;
import com.tfg.kanban.ai.dto.ChatTurn;
import com.tfg.kanban.common.CountByEntity;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.CommentRepository;
import com.tfg.kanban.domain.repository.TaskRepository;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /*
     * Igual que el prompt de AiPlanService, escrito para un modelo pequeno en local.
     * Lo que mas mejora las respuestas no son las reglas de estilo sino las cuatro
     * recetas del final: son las preguntas que la interfaz sugiere al abrir el chat,
     * asi que son las que de verdad se hacen, y sin decirle como contestarlas un 8B
     * se va por las ramas o suelta una parrafada de manual de gestion de proyectos.
     *
     * Nada de markdown a proposito: la respuesta se pinta como texto plano, asi que
     * unos asteriscos de negrita saldrian tal cual en pantalla.
     */
    private static final String SYSTEM_PROMPT = """
            Eres el copiloto de PlanifIA, donde estudiantes universitarios organizan sus practicas
            y trabajos en grupo. Tienes delante el tablero de un workspace concreto y respondes
            preguntas sobre el.

            Respondes SIEMPRE en espanol, en tono cercano y directo, como un companero de clase
            que se ha leido el tablero. Nunca en ingles.

            COMO ESCRIBIR
            - Corto: 120 palabras como mucho, y muchas veces con dos frases sobra.
            - Texto plano: ni asteriscos, ni almohadillas, ni negritas. Para enumerar, guiones.
            - Cita los titulos de las tareas exactamente como aparecen, entre comillas angulares.
            - Al grano: no repitas la pregunta ni empieces con "Claro" o "Por supuesto".
            - Nada de lenguaje de empresa ni de jerga de gestion de proyectos.

            DE DONDE SACAS LO QUE DICES
            - Solo de los datos del workspace que se te dan. No te inventes tareas, personas,
              fechas ni avances que no aparezcan ahi.
            - Si falta el dato para responder, dilo en una frase y di que haria falta.
            - "yo", "me toca" o "mis tareas" se refieren a quien pregunta, que se te indica arriba.
            - Si te preguntan una duda tecnica que no va del tablero, contesta breve y no mezcles
              datos inventados del proyecto.

            LAS PREGUNTAS DE SIEMPRE
            - "Que hago primero": mira sus tareas pendientes y ordena por lo atrasado, luego por
              fecha limite cercana y prioridad alta. Nombra dos o tres, no la lista entera, y di
              en una frase por que van antes.
            - "Como vamos": cuantas terminadas de cuantas, que queda por cerrar y si hay algo
              atrasado o que llegue justo a la entrega.
            - "Quien tiene mas trabajo": compara las tareas pendientes de cada persona y dilo con
              los numeros. Si esta repartido, dilo tambien en vez de forzar un culpable.
            - "Divide esta tarea": tres o cuatro pasos concretos y en orden, cada uno algo que se
              pueda dar por terminado.
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

        // Sin esto, "que hago primero?" no tiene respuesta posible: el modelo no sabe
        // cual de las personas del equipo esta al otro lado.
        Long quienPregunta = SecurityUtils.currentUserId().orElse(null);

        sb.append("\nPersonas del equipo:\n");
        for (TeamMember member : project.getTeam().getMembers()) {
            long suyas = tasks.stream()
                    .filter(task -> task.getAssignee() != null
                            && task.getAssignee().getId().equals(member.getUser().getId()))
                    .filter(task -> !esUltimaColumna(task))
                    .count();
            boolean esQuienPregunta = member.getUser().getId().equals(quienPregunta);
            sb.append("- ").append(member.getUser().getFullName())
                    .append(" (").append(member.getRole().name().toLowerCase()).append("): ")
                    .append(suyas).append(" tareas pendientes")
                    .append(esQuienPregunta ? "  <-- ES QUIEN TE ESTA PREGUNTANDO\n" : "\n");
        }

        if (tasks.isEmpty()) {
            sb.append("\nEl tablero esta vacio: todavia no hay ninguna tarea.\n");
            return sb.toString();
        }

        sb.append("\nTareas del tablero (").append(tasks.size()).append(" en total):\n");
        List<Task> descritas = tasks.stream().limit(MAX_TASKS).toList();
        Map<Long, Long> comentariosPorTarea = commentRepository
                .countByTaskIds(descritas.stream().map(Task::getId).toList()).stream()
                .collect(Collectors.toMap(CountByEntity::entityId, CountByEntity::total));

        for (Task task : descritas) {
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
            long comentarios = comentariosPorTarea.getOrDefault(task.getId(), 0L);
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
