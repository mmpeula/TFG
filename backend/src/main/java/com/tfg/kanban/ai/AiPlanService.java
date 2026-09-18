package com.tfg.kanban.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.ai.dto.AiPlanResponse;
import com.tfg.kanban.ai.dto.ApplyPlanRequest;
import com.tfg.kanban.ai.dto.ProposedTask;
import com.tfg.kanban.board.BoardService;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.TaskRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.project.ProjectService;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.storage.FileStorageService;
import org.springframework.web.multipart.MultipartFile;
import com.tfg.kanban.task.TaskMapper;
import com.tfg.kanban.task.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Planificacion automatica a partir del guion de la practica.
 *
 * El modelo <b>propone</b>: nada se escribe en el tablero hasta que el usuario
 * acepta las tareas que quiere ({@link #apply}). Es la regla de producto del
 * modulo de IA y tambien la razon de separar generar y aplicar en dos endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlanService {

    private static final String SYSTEM_PROMPT = """
            Eres un asistente que planifica practicas universitarias en tableros Kanban
            y reparte el trabajo entre los miembros del equipo.
            Respondes SIEMPRE en espanol y SIEMPRE con un unico objeto JSON valido con esta forma:
            {"tasks":[{"title":"...","reason":"...","type":"EPICA|HISTORIA|TAREA|BUG|INCIDENCIA",
                       "priority":"ALTA|MEDIA|BAJA","estimatedHours":4,"assignee":"Nombre de la persona"}],
             "schedule":{"startDate":"AAAA-MM-DD","deadline":"AAAA-MM-DD","sprintDays":14},
             "excluded":["Nombre de quien no debe recibir ninguna tarea esta vez"]}
            Reglas:
            - "title": accion concreta y verificable, maximo 100 caracteres, empezando por un verbo.
            - "reason": una frase corta explicando por que hace falta, citando el documento cuando puedas.
            - "type": EPICA para un bloque grande que agrupa trabajo, HISTORIA para algo que aporta
              valor al usuario final, TAREA para trabajo tecnico concreto (el caso normal), BUG o
              INCIDENCIA solo si el documento describe corregir algo que ya existe.
            - "priority": solo uno de ALTA, MEDIA o BAJA.
            - "estimatedHours": numero entre 1 y 20.
            - "assignee": el nombre EXACTO de una de las personas de la lista que se te da.
            - No propongas fechas de tarea: el orden de la lista ya indica la secuencia (lo primero
              que sale, lo primero que se hace), y las fechas las calcula el servidor a partir de ese orden.
            - "schedule" es aparte, del PROYECTO entero, no de una tarea: solo rellena cada campo si el
              documento lo dice explicitamente (una fecha de inicio, un plazo de entrega, la duracion de
              cada sprint o iteracion). Si no lo dice, ese campo va a null. Nunca inventes ni calcules una
              fecha por tu cuenta. "sprintDays" siempre en dias (una semana son 7, dos semanas 14).
            - Cubre TODO el trabajo que pide el documento, de principio a fin.
            - Reparte las tareas y las horas de forma equilibrada entre todas las personas.
            - No repitas tareas que ya existan en el tablero.
            - Si el usuario da indicaciones aparte del documento, siguelas: tienen prioridad sobre
              el documento en caso de conflicto (por ejemplo, si pide centrarse solo en una parte
              o cambiar el orden de las prioridades).
            - "excluded": SOLO si las indicaciones piden explicitamente dejar a alguien sin tareas
              esta vez (de baja, con examenes, fuera del equipo esta ronda...), pon aqui su nombre
              EXACTO. Si nadie lo pide, deja la lista vacia: no excluyas a nadie por tu cuenta ni
              repartas de todas formas una tarea a quien esta en esta lista.
            - No anadas texto fuera del JSON.
            """;

    private final AiProperties properties;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final BoardService boardService;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final TaskMapper taskMapper;
    private final DocumentTextExtractor documentTextExtractor;
    private final WorkloadBalancer workloadBalancer;
    private final AiTaskScheduler aiTaskScheduler;

    /** Genera la propuesta a partir del guion ya guardado en el proyecto. */
    @Transactional(readOnly = true)
    public AiPlanResponse generate(Long projectId, Integer people, String indications) {
        Project project = projectService.getProject(projectId);

        if (project.getBriefing() == null || project.getBriefing().isBlank()) {
            throw new BusinessRuleException(
                    "El proyecto no tiene guion de la practica: subelo como documento o escribelo en el proyecto");
        }
        return plan(project, project.getBriefing(), "guion del proyecto", people, indications);
    }

    /**
     * Genera la propuesta a partir de un documento subido. El texto extraido se
     * guarda ademas como guion del proyecto: es la fuente de verdad de la practica.
     */
    @Transactional
    public AiPlanResponse generateFromDocument(Long projectId, MultipartFile file, Integer people, String indications) {
        Project project = projectService.getProject(projectId);
        String text = documentTextExtractor.extract(file);

        project.setBriefing(text);

        return plan(project, text, "documento: " + FileStorageService.cleanFilename(file.getOriginalFilename()),
                people, indications);
    }

    /**
     * Genera la propuesta a partir de un texto escrito a mano en el copiloto
     * («tenemos que hacer una practica de...»). No toca el guion guardado: eso
     * queda reservado al documento oficial de la actividad.
     */
    @Transactional(readOnly = true)
    public AiPlanResponse generateFromText(Long projectId, String text, Integer people, String indications) {
        Project project = projectService.getProject(projectId);
        if (text == null || text.isBlank()) {
            throw new BusinessRuleException("Escribe de que va el trabajo para que pueda proponerte tareas");
        }
        return plan(project, text.trim(), "descripcion escrita", people, indications);
    }

    /** Nunca a ciegas: se recorta para que no desplace el resto del contexto del prompt. */
    private static final int MAX_INDICATIONS_LENGTH = 500;

    private AiPlanResponse plan(Project project, String text, String source, Integer people, String indications) {
        if (!properties.isEnabled()) {
            throw new AiUnavailableException("El modulo de IA esta desactivado en la configuracion del servidor");
        }

        // El DOCENTE solo sigue el equipo: no cuenta para el
        // reparto ni para el maximo de personas entre las que repartir.
        List<TeamMember> members = project.getTeam().getMembers().stream()
                .filter(member -> member.getRole() != Role.DOCENTE)
                .toList();
        List<Task> existing = existingTasks(project);
        String notas = indications == null || indications.isBlank() ? null
                : indications.strip().length() > MAX_INDICATIONS_LENGTH
                        ? indications.strip().substring(0, MAX_INDICATIONS_LENGTH)
                        : indications.strip();

        String content = ollamaClient.chatJson(SYSTEM_PROMPT,
                buildUserPrompt(project, text, existing, members, people, notas));

        ParsedResponse parsed = parse(content);
        List<ProposedTask> tasks = withoutDuplicates(parsed.tasks(), existing);
        int duplicates = parsed.tasks().size() - tasks.size();

        // Se resuelve aqui, con la lista completa, y no dentro de WorkloadBalancer:
        // este ya garantiza que nadie se queda sin tareas, asi que si a alguien
        // excluido se le dejara en la lista igualmente acabaria con una. La unica
        // forma fiable de excluir a alguien es que no este ni siquiera en el reparto.
        List<String> excludedNames = new ArrayList<>();
        List<TeamMember> activeMembers = new ArrayList<>(members);
        for (String raw : parsed.excluded()) {
            WorkloadBalancer.matchName(raw, activeMembers).ifPresent(id -> {
                activeMembers.removeIf(member -> member.getUser().getId().equals(id));
                members.stream()
                        .filter(member -> member.getUser().getId().equals(id))
                        .findFirst()
                        .ifPresent(member -> excludedNames.add(member.getUser().getFullName()));
            });
        }

        // Si el documento acaba de decir las fechas, son la fuente de verdad para
        // ESTA propuesta: usar en su lugar las que ya tuviera guardadas el workspace
        // (normalmente ninguna, si es la primera vez) es lo que hacia que las tareas
        // cayeran en una ventana que no tenia nada que ver con lo que el propio
        // aviso de "el documento dice..." acababa de mostrar. Las fechas del
        // workspace no cambian aqui: eso solo pasa si el usuario pulsa "Usar estos
        // datos" en esa tarjeta.
        LocalDate startDate = fechaPropuestaOGuardada(parsed.schedule() == null ? null : parsed.schedule().startDate(),
                project.getStartDate());
        LocalDate deadline = fechaPropuestaOGuardada(parsed.schedule() == null ? null : parsed.schedule().deadline(),
                project.getDeadline());

        WorkloadBalancer.Result reparto = workloadBalancer.distribute(tasks, activeMembers, people);
        List<ProposedTask> agendadas = aiTaskScheduler.schedule(reparto.tasks(), startDate, deadline);

        log.info("IA: {} tareas para el proyecto {} repartidas entre {} personas ({} descartadas por repetidas, "
                        + "{} excluidas por indicacion)",
                agendadas.size(), project.getId(), reparto.workload().size(), duplicates, excludedNames.size());

        return AiPlanResponse.of(project.getId(), properties.getModel(), source,
                agendadas, reparto.workload(), duplicates, parsed.schedule(), excludedNames);
    }

    /** Lleva al tablero solo las tareas que el usuario ha aceptado. */
    @Transactional
    public List<TaskResponse> apply(Long projectId, ApplyPlanRequest request) {
        Project project = projectService.getProject(projectId);
        Board board = boardService.getBoard(request.boardId());

        if (!board.getProject().getId().equals(projectId)) {
            throw new BusinessRuleException("El tablero indicado no pertenece a este proyecto");
        }
        BoardColumn column = board.getColumns().stream()
                .min((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
                .orElseThrow(() -> new BusinessRuleException("El tablero no tiene columnas"));

        User creator = SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null);
        int position = (int) taskRepository.countByBoardColumn_Id(column.getId());

        // Solo se asigna a gente que pertenezca al equipo del proyecto.
        Set<Long> allowed = project.getTeam().getMembers().stream()
                .map(member -> member.getUser().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<Task> created = new ArrayList<>();
        for (ProposedTask proposed : request.tasks()) {
            User assignee = proposed.assigneeId() != null && allowed.contains(proposed.assigneeId())
                    ? userRepository.findById(proposed.assigneeId()).orElse(null)
                    : null;

            created.add(taskRepository.save(Task.builder()
                    .title(proposed.title().trim())
                    .description(proposed.reason())
                    .type(proposed.type() == null ? TaskType.TAREA : proposed.type())
                    .priority(proposed.priority() == null ? TaskPriority.MEDIA : proposed.priority())
                    .estimatedHours(proposed.estimatedHours())
                    .dueDate(proposed.dueDate())
                    .boardColumn(column)
                    .assignee(assignee)
                    .createdBy(creator)
                    .aiGenerated(true)
                    .position(position++)
                    .build()));
        }

        activityService.record(project, ActivityAction.GENERACION_IA, "Board", board.getId(),
                "Anadio %d %s generadas con IA a '%s'".formatted(
                        created.size(), created.size() == 1 ? "tarea" : "tareas", column.getName()));

        return taskMapper.toResponses(created);
    }

    /** La que acaba de proponer el documento si hay una; si no, la que ya tuviera guardada. */
    private LocalDate fechaPropuestaOGuardada(LocalDate propuesta, LocalDate guardada) {
        return propuesta != null ? propuesta : guardada;
    }

    private List<Task> existingTasks(Project project) {
        if (project.getBoards().isEmpty()) return List.of();
        return taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(project.getBoards().get(0).getId());
    }

    /**
     * Al modelo se le pide que no repita tareas, pero un modelo pequeno lo ignora a
     * menudo: la comprobacion se hace tambien aqui, que es determinista.
     */
    private List<ProposedTask> withoutDuplicates(List<ProposedTask> proposed, List<Task> existing) {
        List<String> known = existing.stream().map(task -> normalize(task.getTitle())).toList();

        List<ProposedTask> result = new ArrayList<>();
        for (ProposedTask task : proposed) {
            String candidate = normalize(task.title());
            boolean repeated = known.stream()
                    .anyMatch(title -> title.equals(candidate)
                            || (title.length() > 12 && (title.contains(candidate) || candidate.contains(title))));
            if (repeated) {
                log.debug("IA: descartada «{}» por repetir una tarea existente", task.title());
                continue;
            }
            result.add(task);
        }
        return result;
    }

    /** Sin tildes, sin mayusculas y sin palabras vacias, para comparar titulos. */
    private String normalize(String value) {
        String plain = java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ");
        return java.util.Arrays.stream(plain.split("\\s+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !List.of("del", "las", "los", "una", "para", "con", "que").contains(word))
                .reduce((a, b) -> a + " " + b)
                .orElse("")
                .trim();
    }

    /** Contexto real: documento, fecha, personas concretas y lo que ya hay en el tablero. */
    private String buildUserPrompt(Project project, String text, List<Task> existing,
                                   List<TeamMember> members, Integer people, String indications) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Practica: ").append(project.getName()).append('\n');

        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            prompt.append("Descripcion: ").append(project.getDescription()).append('\n');
        }
        if (project.getDeadline() != null) {
            // ChronoUnit y no Period: el componente getDays() de un Period solo lleva el
            // resto de dias del ultimo mes e ignora los anos, asi que una entrega a mas
            // de un ano vista le llegaba al modelo con un plazo mucho mas corto del real.
            long dias = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), project.getDeadline());
            prompt.append("Fecha de entrega: ").append(project.getDeadline())
                    .append(dias >= 0 ? " (quedan unos " + dias + " dias)" : " (ya vencida)")
                    .append('\n');
        }

        List<TeamMember> reparto = (people != null && people > 0 && people < members.size())
                ? members.subList(0, people)
                : members;
        prompt.append("Personas del equipo entre las que hay que repartir el trabajo (")
              .append(reparto.size()).append("):\n");
        reparto.forEach(member -> prompt.append("- ").append(member.getUser().getFullName()).append('\n'));

        if (!existing.isEmpty()) {
            prompt.append("Tareas que YA existen en el tablero (no las repitas):\n");
            existing.forEach(task -> prompt.append("- ").append(task.getTitle()).append('\n'));
        }

        prompt.append("\nDocumento de la actividad:\n").append(text).append('\n');

        if (indications != null && !indications.isBlank()) {
            prompt.append("\nIndicaciones del usuario para esta propuesta (tienen prioridad sobre el ")
                  .append("documento si hay conflicto):\n").append(indications).append('\n');
        }

        prompt.append("\nPropon como maximo ").append(properties.getMaxTasks())
              .append(" tareas que cubran todo el trabajo del documento, asignando cada una a una de las ")
              .append(reparto.size()).append(" personas de la lista y equilibrando las horas entre ellas.");

        return prompt.toString();
    }

    /**
     * Tareas propuestas, mas las fechas/duracion de sprint que el documento
     * menciona y a quien hay que dejar fuera del reparto esta vez, si las
     * indicaciones lo piden. "excluded" son los nombres tal cual los da el
     * modelo, todavia sin resolver contra el equipo real.
     */
    private record ParsedResponse(
            List<ProposedTask> tasks, AiPlanResponse.SuggestedSchedule schedule, List<String> excluded) {
    }

    /**
     * El modelo devuelve JSON, pero no siempre con la forma pedida: se acepta tanto
     * {"tasks":[...]} como un array suelto, y se descarta lo que no sea utilizable.
     */
    private ParsedResponse parse(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode array = root.isArray() ? root : root.path("tasks");
            if (!array.isArray()) {
                array = root.path("tareas");
            }
            if (!array.isArray()) {
                throw new AiUnavailableException("El modelo no ha devuelto una lista de tareas");
            }

            List<ProposedTask> tasks = new ArrayList<>();
            for (JsonNode node : array) {
                String title = node.path("title").asText(node.path("titulo").asText("")).trim();
                if (title.isBlank()) continue;

                tasks.add(new ProposedTask(
                        title.length() > 200 ? title.substring(0, 200) : title,
                        text(node, "reason", "motivo", 300),
                        type(node.path("type").asText(node.path("tipo").asText(""))),
                        priority(node.path("priority").asText(node.path("prioridad").asText(""))),
                        hours(node),
                        null,
                        null,
                        text(node, "assignee", "asignado", 150)));

                if (tasks.size() >= properties.getMaxTasks()) break;
            }
            // Un array suelto (sin objeto raiz) no puede traer "schedule" ni "excluded": no hay donde leerlos.
            return new ParsedResponse(
                    tasks,
                    root.isArray() ? null : schedule(root.path("schedule")),
                    root.isArray() ? List.of() : excludedNames(root.path("excluded")));

        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Respuesta del modelo no interpretable: {}", abbreviate(content));
            throw new AiUnavailableException(
                    "El modelo ha respondido algo que no se ha podido interpretar. Vuelve a intentarlo.", ex);
        }
    }

    private String text(JsonNode node, String field, String alias, int max) {
        String value = node.path(field).asText(node.path(alias).asText("")).trim();
        if (value.isBlank()) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    private TaskPriority priority(String raw) {
        if (raw == null || raw.isBlank()) return TaskPriority.MEDIA;
        try {
            return TaskPriority.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return TaskPriority.MEDIA;
        }
    }

    /** Si el modelo no dice el tipo o inventa uno que no existe, se deja en blanco: se lee como TAREA. */
    private TaskType type(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return TaskType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private BigDecimal hours(JsonNode node) {
        JsonNode value = node.has("estimatedHours") ? node.path("estimatedHours") : node.path("horas");
        if (value.isMissingNode() || value.isNull()) return null;
        double hours = value.asDouble(0);
        if (hours <= 0 || hours > 100) return null;
        return BigDecimal.valueOf(hours);
    }

    /**
     * A diferencia de tipo o prioridad, aqui no hay un valor por defecto seguro:
     * una fecha inventada es activamente enganosa, asi que cualquier cosa que no
     * se pueda interpretar con confianza se deja en null en vez de adivinar.
     */
    private AiPlanResponse.SuggestedSchedule schedule(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        LocalDate startDate = date(node, "startDate", "fechaInicio");
        LocalDate deadline = date(node, "deadline", "fechaEntrega");
        Integer sprintDays = sprintDays(node);
        return new AiPlanResponse.SuggestedSchedule(startDate, deadline, sprintDays);
    }

    private LocalDate date(JsonNode node, String field, String alias) {
        String raw = node.path(field).asText(node.path(alias).asText("")).trim();
        if (raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (java.time.format.DateTimeParseException ex) {
            log.debug("IA: fecha de planificacion no interpretable, se ignora: {}", raw);
            return null;
        }
    }

    private Integer sprintDays(JsonNode node) {
        JsonNode value = node.has("sprintDays") ? node.path("sprintDays") : node.path("duracionSprint");
        if (value.isMissingNode() || value.isNull()) return null;
        int days = value.asInt(0);
        return days >= 1 && days <= 90 ? days : null;
    }

    /** Nombres tal cual, sin resolver: eso lo hace {@link #plan} contra el equipo real. */
    private List<String> excludedNames(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> names = new ArrayList<>();
        for (JsonNode item : node) {
            String name = item.asText("").trim();
            if (!name.isBlank()) names.add(name);
        }
        return names;
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        return value.length() <= 300 ? value : value.substring(0, 300) + "…";
    }
}
