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

    /*
     * Escrito para un modelo pequeno corriendo en local (8B), que es lo que hay al
     * otro lado. De ahi tres decisiones que no se tomarian con un modelo grande:
     * un ejemplo completo y relleno en vez del esquema con puntos suspensivos,
     * porque un modelo pequeno imita mucho mejor de lo que deduce; reglas cortas
     * agrupadas por bloques con titulo, porque en una lista plana y larga se le
     * pierden las importantes; y ni una sola regla sobre cosas que el servidor ya
     * decide solo (las fechas de cada tarea y el equilibrio de horas), porque
     * pedirselas le gasta atencion y ademas invita a contradecir lo que luego se
     * va a recalcular igualmente.
     *
     * La prohibicion de ceremonias es la regla que mas cambia el resultado: sin
     * ella el modelo describe el proceso Scrum que ha visto mil veces al
     * entrenarse ("planificar el sprint 1", "reunion de retrospectiva") en lugar
     * del trabajo que pide el enunciado que tiene delante.
     */
    private static final String SYSTEM_PROMPT = """
            Eres quien planifica practicas universitarias: lees el enunciado y lo conviertes en la
            lista de tareas que el equipo tiene que hacer.

            Respondes SIEMPRE en espanol y SIEMPRE con un unico objeto JSON, sin nada de texto
            alrededor. Una respuesta valida se ve exactamente asi:

            {"tasks":[
              {"title":"Disenar el modelo entidad-relacion","reason":"El apartado 2 pide el diagrama E/R antes de crear las tablas","type":"TAREA","priority":"ALTA","estimatedHours":5,"assignee":"Ana Garcia"},
              {"title":"Escribir el script SQL de creacion","reason":"Sin las tablas no se pueden probar las consultas","type":"TAREA","priority":"ALTA","estimatedHours":4,"assignee":"Luis Perez"},
              {"title":"Redactar la memoria","reason":"El enunciado la pide como entregable final","type":"TAREA","priority":"MEDIA","estimatedHours":6,"assignee":"Ana Garcia"}],
             "schedule":{"startDate":null,"deadline":"2027-01-15","sprintDays":14},
             "excluded":[]}

            QUE CUENTA COMO TAREA
            - Un entregable concreto del enunciado: algo que alguien hace y se puede dar por terminado.
            - El titulo empieza por un verbo, dice QUE se entrega y no pasa de 100 caracteres.
            - Nada de ceremonias ni de relleno: no propongas reuniones, planificaciones de sprint,
              revisiones, retrospectivas, ni titulos vacios tipo "hacer la segunda tarea". Si no sale
              del enunciado, no va en la lista.
            - Ni enormes ni minimas: de 1 a 20 horas cada una. Lo que pase de 20 horas, partelo.

            EL ORDEN DE LA LISTA ES EL ORDEN DEL TRABAJO
            - Primero lo que hay que hacer antes. El servidor reparte las tareas por iteraciones
              siguiendo ese orden, asi que una lista desordenada da un calendario desordenado.
            - No pongas fechas en las tareas: las calcula el servidor.

            CAMPOS
            - "reason": una frase corta, citando el enunciado cuando puedas.
            - "type": TAREA para trabajo tecnico (el caso normal), HISTORIA si aporta valor visible a
              quien usa lo que se construye, EPICA para un bloque que agrupa varias, BUG o INCIDENCIA
              solo para arreglar algo que ya existe.
            - "priority": ALTA, MEDIA o BAJA. ALTA es lo que bloquea al resto del trabajo.
            - "assignee": el nombre EXACTO de una de las personas de la lista. Repartelas entre todas,
              no se lo des todo a la misma.
            - "excluded": lista vacia, salvo que las indicaciones pidan dejar a alguien sin tareas esta
              vez; entonces va ahi su nombre exacto.

            "schedule" ES DEL PROYECTO ENTERO, NO DE UNA TAREA
            - Rellena cada campo solo si el enunciado lo dice; si no lo dice, null. No calcules fechas
              por tu cuenta.
            - Usa el calendario de hoy, cuya fecha se te da en el mensaje: si el enunciado da el dia y
              el mes pero no el ano, elige el ano que deja la fecha en el futuro. Nunca un plazo vencido.
            - "sprintDays" es lo que dura UNA iteracion, en dias, entre 7 y 28. No es lo que dura la
              practica entera.

            Y ADEMAS
            - Cubre el enunciado de principio a fin, sin inventarte trabajo que no pide.
            - No repitas ninguna tarea que ya este en el tablero.
            - Si las indicaciones del usuario contradicen al enunciado, mandan las indicaciones.
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

        // La duracion de sprint con la que se fechan las tareas es la misma que va a
        // acabar teniendo el workspace: la que dice el documento si la dice, y si no
        // la que ya tenga guardada. Con otra, las tareas caerian entre los sprints
        // que luego pinta el plan.
        Integer sprintDays = parsed.schedule() != null && parsed.schedule().sprintDays() != null
                ? parsed.schedule().sprintDays()
                : project.getSprintDays();

        WorkloadBalancer.Result reparto = workloadBalancer.distribute(tasks, activeMembers, people);
        List<ProposedTask> agendadas = aiTaskScheduler.schedule(reparto.tasks(), startDate, deadline, sprintDays);

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
        // Sin esto el modelo planifica en el ano en que se entreno y propone un curso
        // ya terminado: las fechas quedan vencidas y no encajan con ningun sprint real.
        prompt.append("Hoy es ").append(LocalDate.now()).append('\n');
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

            // Cuantas iteraciones caben de verdad. Saberlo le da la escala del plan: si
            // solo hay dos, no tiene sentido proponer trabajo para medio curso.
            int iteraciones = aiTaskScheduler.sprintsOf(
                    aiTaskScheduler.resolveWindow(project.getStartDate(), project.getDeadline()),
                    project.getSprintDays()).size();
            prompt.append("El trabajo se repartira en ").append(iteraciones)
                    .append(iteraciones == 1 ? " iteracion" : " iteraciones").append('\n');
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
              .append(" tareas, en el orden en que hay que hacerlas, que cubran el documento entero, ")
              .append("cada una asignada a una de las ").append(reparto.size())
              .append(" personas de la lista. Solo entregables del documento: ni reuniones ni ")
              .append("planificaciones ni titulos genericos.");

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

        Ventana ventana = alCalendarioActual(startDate, deadline);
        return new AiPlanResponse.SuggestedSchedule(ventana.inicio(), ventana.entrega(), sprintDays(node));
    }

    /** Fechas del proyecto ya llevadas al calendario en curso. Cualquiera de las dos puede faltar. */
    private record Ventana(LocalDate inicio, LocalDate entrega) {
    }

    /**
     * Corrige el ano de las fechas que propone el modelo.
     *
     * <p>El dia y el mes salen del enunciado («entregar el 16 de diciembre»), pero
     * el ano lo pone el modelo de su cosecha y un modelo pequeno tira del curso que
     * vio al entrenarse, asi que propone un plazo ya vencido. Descartarlo sin mas
     * perderia lo unico que el documento si dice; en su lugar se adelanta en anos
     * enteros hasta caer en el futuro, que es lo que el usuario quiere decir cuando
     * escribe una fecha sin ano.</p>
     *
     * <p>El desplazamiento se calcula una sola vez y se aplica a las dos fechas para
     * no romper el intervalo: un curso que va de diciembre a enero cruza el cambio
     * de ano, y moverlas por separado dejaria la entrega antes del inicio.</p>
     */
    private Ventana alCalendarioActual(LocalDate inicio, LocalDate entrega) {
        LocalDate hoy = LocalDate.now();
        LocalDate referencia = entrega != null ? entrega : inicio;
        if (referencia == null) {
            return new Ventana(null, null);
        }

        int anos = 0;
        while (referencia.plusYears(anos).isBefore(hoy)) {
            anos++;
        }
        if (anos > 0) {
            log.debug("IA: fechas propuestas en un curso ya pasado, adelantadas {} ano(s)", anos);
        }

        LocalDate nuevoInicio = inicio == null ? null : inicio.plusYears(anos);
        LocalDate nuevaEntrega = entrega == null ? null : entrega.plusYears(anos);

        // Si aun asi la entrega no queda despues del inicio, la pareja no describe
        // ninguna ventana de trabajo y la fecha de inicio es la que sobra.
        if (nuevoInicio != null && nuevaEntrega != null && !nuevaEntrega.isAfter(nuevoInicio)) {
            return new Ventana(null, nuevaEntrega);
        }
        return new Ventana(nuevoInicio, nuevaEntrega);
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

    /** Lo que dura un sprint de verdad: de una a cuatro semanas. */
    private static final int MIN_SPRINT_DAYS = 7;
    private static final int MAX_SPRINT_DAYS = 28;

    /**
     * Una iteracion de dos meses no es un sprint, es el proyecto entero: el modelo
     * confunde a menudo la duracion del sprint con la del trabajo y devuelve cosas
     * como 56 o 90 dias. Fuera del rango razonable se ignora, y el plan se queda
     * con la duracion que ya tuviera el workspace (o las dos semanas por defecto).
     */
    private Integer sprintDays(JsonNode node) {
        JsonNode value = node.has("sprintDays") ? node.path("sprintDays") : node.path("duracionSprint");
        if (value.isMissingNode() || value.isNull()) return null;
        int days = value.asInt(0);
        if (days < MIN_SPRINT_DAYS || days > MAX_SPRINT_DAYS) {
            log.debug("IA: duracion de sprint fuera de rango ({} dias), se ignora", days);
            return null;
        }
        return days;
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
