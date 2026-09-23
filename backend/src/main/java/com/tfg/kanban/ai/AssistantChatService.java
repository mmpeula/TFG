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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * El asistente conversacional del workspace.
 *
 * <p>A diferencia de {@link AiPlanService}, que propone tareas para el tablero,
 * aqui el modelo solo <b>responde</b>: no escribe nada. Lo que hace util la
 * respuesta es el contexto, que se arma con el estado real del tablero (tareas,
 * columnas, fechas, personas) en lugar de dejar que el modelo se lo imagine.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantChatService {

    /*
     * Igual que el prompt de AiPlanService, escrito para un modelo pequeno en local.
     * Lo que mas mejora las respuestas no son las reglas de estilo sino las recetas
     * del final: son las preguntas que la interfaz sugiere al abrir el chat, asi que
     * son las que de verdad se hacen, y sin decirle como contestarlas un 8B se va por
     * las ramas o suelta una parrafada de manual de gestion de proyectos.
     *
     * Nada de markdown a proposito: la respuesta se pinta como texto plano, asi que
     * unos asteriscos de negrita saldrian tal cual en pantalla.
     */
    private static final String SYSTEM_PROMPT = """
            Eres el asistente de PlanifIA: un gestor de proyectos con ojo clinico para leer un
            tablero Kanban y decir, sin rodeos, que esta pasando de verdad. Estudiantes
            universitarios te preguntan sobre el tablero de un workspace concreto, que tienes
            delante con todos sus datos.

            No eres un buscador que repite lo que ya esta en pantalla: cruzas fechas, prioridades,
            responsables y tiempo sin moverse para sacar una conclusion que a simple vista no se ve.
            "Vais tarde", "esto esta parado" o "fulanito lleva el doble que los demas" son el tipo de
            cosas que se te tienen que ocurrir.

            Respondes SIEMPRE en espanol, en tono cercano y directo, como un companero de clase que
            se ha leido el tablero entero antes de contestar. Nunca en ingles.

            COMO ESCRIBIR
            - Corto: 120 palabras como mucho, y muchas veces con dos frases sobra.
            - Texto plano: ni asteriscos, ni almohadillas, ni negritas. Para enumerar, guiones.
            - Cita los titulos de las tareas exactamente como aparecen, entre comillas angulares.
            - Numeros, no vaguedades: "3 de 8 terminadas" dice mas que "vais bien". Si el dato para
              un numero exacto no esta, no lo inventes: describe lo que sí ves.
            - Al grano: no repitas la pregunta ni empieces con "Claro" o "Por supuesto".
            - Nada de lenguaje de empresa ni de jerga de gestion de proyectos.

            DE DONDE SACAS LO QUE DICES
            - Solo de los datos del workspace que se te dan. No te inventes tareas, personas,
              fechas, comentarios ni avances que no aparezcan ahi.
            - Si falta el dato para responder con precision, dilo en una frase y di que haria falta
              para saberlo, en vez de rellenar el hueco con una suposicion.
            - "yo", "me toca" o "mis tareas" se refieren a quien pregunta, que se te indica arriba
              marcado como quien esta al otro lado de la conversacion.
            - Si te preguntan una duda tecnica que no va del tablero, contesta breve y no mezcles
              datos inventados del proyecto.

            LAS PREGUNTAS DE SIEMPRE
            - "Que hago primero": de sus tareas pendientes, ordena por lo que ya esta atrasado,
              luego por fecha limite cercana y prioridad alta, y por ultimo por lo que lleva mas
              tiempo parado. Nombra dos o tres, no la lista entera, y di en una frase por que van
              antes que el resto.
            - "Como vamos": cuantas terminadas de cuantas en total, cuantas quedan por cerrar, y si
              hay algo atrasado, a punto de vencer, o parado sin moverse. Si con los dias que quedan
              hasta la entrega y lo que falta por cerrar el ritmo pinta ajustado, dilo tal cual.
            - "Quien tiene mas trabajo": compara las tareas pendientes de cada persona con sus
              numeros exactos. Si alguien dobla al resto, nombralo; si esta repartido, dilo tambien
              en vez de forzar un culpable donde no lo hay.
            - "Hay algo bloqueado o parado": busca tareas marcadas como sin mover desde hace dias o
              sin nadie asignado en una columna de trabajo. Si hay alguna, la que lleve mas tiempo
              parada primero, y di desde cuando. Si no hay ninguna, dilo en una frase y no te
              inventes un problema para tener algo que contar.
            - "Divide esta tarea": tres o cuatro pasos concretos y en orden, cada uno algo que se
              pueda dar por terminado, no fases abstractas tipo "analisis" o "implementacion".
            """;

    /** Tope de turnos anteriores que se reenvian: mas no cabe junto al contexto. */
    private static final int MAX_HISTORY = 6;

    /** Tope de tareas que se describen. Un tablero de practicas rara vez pasa de aqui. */
    private static final int MAX_TASKS = 60;

    /**
     * Dias sin tocar una tarea a partir de los cuales se cuenta como parada.
     *
     * Mismo umbral que {@code lib/signals.ts} en el frontend (DIAS_PARADA): son la
     * misma senal contada dos veces, una para pintar la insignia del tablero y otra
     * para que el asistente pueda hablar de ella cuando le preguntan.
     */
    private static final int DIAS_PARADA = 4;

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

        log.info("Asistente: respuesta para el workspace {} con {} tareas de contexto", workspaceId, tasks.size());
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
            diasParada(task, hoy).ifPresent(dias ->
                    sb.append(" | sin mover desde hace ").append(dias).append(" dias (PARADA)"));
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

    /**
     * Ni "por hacer" (nadie la ha tocado todavia, no es que este atascada) ni
     * "hecho" (ya no importa cuanto tardo). Solo cuenta como parada una tarea que
     * esta en marcha y lleva dias sin moverse: son las columnas de en medio.
     */
    private boolean esColumnaDeTrabajo(Task task) {
        List<BoardColumn> columns = task.getBoardColumn().getBoard().getColumns();
        if (columns.size() < 3) return false;
        Long columnId = task.getBoardColumn().getId();
        return !columns.get(0).getId().equals(columnId)
                && !columns.get(columns.size() - 1).getId().equals(columnId);
    }

    /**
     * Dias sin mover una tarea en curso, si hace suficientes como para contar.
     *
     * <p>Sin esta senal el asistente solo podia hablar de fechas limite, y una
     * tarea sin fecha que lleva dos semanas parada en "en curso" pasaba
     * completamente desapercibida aunque sea justo el tipo de cosa que alguien
     * preguntaria «hay algo bloqueado?» para saber.</p>
     */
    private Optional<Long> diasParada(Task task, LocalDate hoy) {
        if (!esColumnaDeTrabajo(task)) return Optional.empty();
        Instant ultimoMovimiento = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
        if (ultimoMovimiento == null) return Optional.empty();

        long dias = ChronoUnit.DAYS.between(
                ultimoMovimiento.atZone(ZoneId.systemDefault()).toLocalDate(), hoy);
        return dias >= DIAS_PARADA ? Optional.of(dias) : Optional.empty();
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
