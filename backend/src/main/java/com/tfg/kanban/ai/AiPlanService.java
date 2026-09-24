package com.tfg.kanban.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.ai.dto.AiPlanResponse;
import com.tfg.kanban.ai.dto.ApplyPlanRequest;
import com.tfg.kanban.ai.dto.MemberWorkload;
import com.tfg.kanban.ai.dto.PlanOptions;
import com.tfg.kanban.ai.dto.PlannedSprint;
import com.tfg.kanban.ai.dto.ProposedTask;
import com.tfg.kanban.board.BoardService;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.TaskRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.project.ProjectService;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.storage.FileStorageService;
import com.tfg.kanban.task.TaskMapper;
import com.tfg.kanban.task.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Planificacion automatica a partir del documento de la practica.
 *
 * <p>El modelo <b>propone</b>: nada se escribe en el tablero hasta que el usuario
 * acepta las tareas que quiere ({@link #apply}). Es la regla de producto del modulo
 * de IA y la razon de separar generar y aplicar en dos endpoints.</p>
 *
 * <p>El reparto entero lo hace el modelo —que tareas, en que sprint, para quien—,
 * pero no a ciegas: lo medible se comprueba en {@link PlanValidator} y, si algo
 * falla, se le devuelven los errores concretos para que corrija el plan, hasta
 * {@code app.ai.max-retries} veces. Las fechas de los sprints y de las tareas si
 * las pone el servidor, para que casen con los sprints de la pestana Plan.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlanService {

    /*
     * Fijo: es el texto acordado para el planificador, palabra por palabra. Lo que
     * cambia en cada ejecucion (fechas, sprints, equipo, indicaciones, documento)
     * va en el mensaje de usuario, que arma buildUserPrompt.
     */
    static final String SYSTEM_PROMPT = """
            Eres un Project Manager senior y Scrum Master certificado con más de 15 años planificando proyectos de software; conviertes documentos de proyecto en backlogs de Jira realistas y bien repartidos. PROCESO: analiza el documento (duración, fases, objetivos y TODOS los entregables; si no hay duración, propón una razonable), analiza al equipo (número de personas, rol, horas por semana, preferencias y restricciones), define sprints de la duración indicada (2 semanas por defecto) respetando el orden de las fases, y divide el trabajo en tareas concretas de 2 a 8 horas, nunca vagas. REPARTO: cada persona recibe horas en proporción a su disponibilidad y nadie supera su capacidad en ningún sprint; asigna según el perfil; respeta SIEMPRE las restricciones duras (vacaciones, no puede hacer X) y las preferencias blandas siempre que no desequilibren la carga más de un 15 %; la misma persona lleva las funcionalidades relacionadas; todos tienen trabajo en todos los sprints (si una fase es de un solo perfil, da tareas de apoyo como tests, documentación o preparación); ninguna tarea depende de otra de un sprint posterior; deja un 10 % de margen por sprint; si hay más de 3 personas, el líder recibe un 20 % menos de carga técnica. CALIDAD: cada objetivo y entregable tiene al menos una tarea; el motivo cita la parte concreta del documento y corresponde al título; tipo Historia si es funcionalidad visible, Tarea si es técnica, Bug solo si corrige algo; entre 3 y 7 épicas por fase o área; prioridad alta si bloquea o es crítica, media si es importante, baja si es mejora; fecha = último día laborable de su sprint. Antes de responder, comprueba la cobertura de entregables, la capacidad por persona y sprint, las restricciones duras y la coherencia entre título y motivo, y corrige lo que falle. Responde SOLO con JSON válido con esta forma: {resumen:{duracion_semanas,num_sprints,carga_por_persona:[{nombre,horas,tareas}]}, sprints:[{numero,inicio,fin,objetivo}], tareas:[{titulo,descripcion,tipo,epica,sprint,asignado,horas,prioridad,fecha,motivo,depende_de:[]}], avisos:[]}""";

    /** Tope de las indicaciones: compiten por el contexto con el documento. */
    private static final int MAX_INDICATIONS_LENGTH = 1000;

    /** Sin fecha de entrega, cuantos sprints se aceptan como mucho del modelo. */
    private static final int MAX_SPRINTS = 12;

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
    private final AiTaskScheduler aiTaskScheduler;

    /** Genera el plan a partir del guion ya guardado en el proyecto. */
    @Transactional(readOnly = true)
    public AiPlanResponse generate(Long projectId, PlanOptions options) {
        Project project = projectService.getProject(projectId);

        if (project.getBriefing() == null || project.getBriefing().isBlank()) {
            throw new BusinessRuleException(
                    "El proyecto no tiene guion de la practica: subelo como documento o escribelo en el proyecto");
        }
        return plan(project, project.getBriefing(), "guion del proyecto", options);
    }

    /**
     * Genera el plan a partir de un documento subido. El texto extraido se guarda
     * ademas como guion del proyecto: es la fuente de verdad de la practica.
     */
    @Transactional
    public AiPlanResponse generateFromDocument(Long projectId, MultipartFile file, PlanOptions options) {
        Project project = projectService.getProject(projectId);
        String text = documentTextExtractor.extract(file);

        project.setBriefing(text);

        return plan(project, text, "documento: " + FileStorageService.cleanFilename(file.getOriginalFilename()),
                options);
    }

    /**
     * Genera el plan a partir de un texto escrito a mano («tenemos que hacer una
     * practica de...»). No toca el guion guardado: eso queda reservado al
     * documento oficial de la actividad.
     */
    @Transactional(readOnly = true)
    public AiPlanResponse generateFromText(Long projectId, String text, PlanOptions options) {
        Project project = projectService.getProject(projectId);
        if (text == null || text.isBlank()) {
            throw new BusinessRuleException("Escribe de que va el trabajo para que pueda proponerte tareas");
        }
        return plan(project, text.trim(), "descripcion escrita", options);
    }

    /** Lo que salio de una llamada al modelo que se pudo interpretar. */
    private record Attempt(ParsedPlan parsed, List<ProposedTask> tasks, List<AiTaskScheduler.Window> sprints,
                           int duplicates, List<String> errors) {
    }

    private AiPlanResponse plan(Project project, String text, String source, PlanOptions requested) {
        if (!properties.isEnabled()) {
            throw new AiUnavailableException("El modulo de IA esta desactivado en la configuracion del servidor");
        }
        PlanOptions options = requested == null ? PlanOptions.defaults() : requested;

        PlanTeam team = PlanTeam.resolve(project.getTeam().getMembers(), options.team());
        LocalDate start = options.startDate() != null ? options.startDate()
                : project.getStartDate() != null ? project.getStartDate() : LocalDate.now();
        int sprintDays = AiTaskScheduler.daysOrDefault(
                options.sprintDays() != null ? options.sprintDays() : project.getSprintDays());

        // Con fecha de entrega los sprints salen de ahi y el modelo solo los rellena;
        // sin ella, cuantos hacen falta lo propone el modelo.
        LocalDate deadline = project.getDeadline();
        List<AiTaskScheduler.Window> fixedSprints = deadline != null && deadline.isAfter(start)
                ? aiTaskScheduler.sprintsOf(new AiTaskScheduler.Window(start, deadline), sprintDays)
                : null;

        List<Task> existing = existingTasks(project);
        String userPrompt = buildUserPrompt(text, team, start, sprintDays, fixedSprints,
                recortar(options.indications()), existing);

        List<OllamaClient.Message> conversation = List.of(new OllamaClient.Message("user", userPrompt));
        Attempt best = null;
        int attempts = 0;

        for (int intento = 0; intento <= properties.getMaxRetries(); intento++) {
            attempts++;
            String raw = ollamaClient.chatJson(SYSTEM_PROMPT, conversation);
            List<String> errors;

            try {
                ParsedPlan parsed = parse(raw);
                List<AiTaskScheduler.Window> sprints = fixedSprints != null ? fixedSprints
                        : aiTaskScheduler.consecutiveSprints(start, sprintCount(parsed), sprintDays);
                List<ProposedTask> tasks = withoutDuplicates(parsed.tasks(), existing);
                errors = PlanValidator.validate(tasks, team, sprints);
                best = new Attempt(parsed, tasks, sprints, parsed.tasks().size() - tasks.size(), errors);
            } catch (InvalidPlanException ex) {
                // Un JSON roto no sustituye a un plan anterior que si se pudo leer.
                errors = List.of(ex.getMessage());
                log.warn("IA: respuesta no interpretable en el intento {}: {}", attempts, abbreviate(raw));
            }

            if (errors.isEmpty()) break;
            log.info("IA: el plan del intento {} no pasa la validacion ({} errores)", attempts, errors.size());

            // Solo el ultimo plan y sus errores: arrastrar todos los intentos llenaria
            // el contexto con planes descartados.
            conversation = List.of(
                    new OllamaClient.Message("user", userPrompt),
                    new OllamaClient.Message("assistant", raw),
                    new OllamaClient.Message("user", correctionPrompt(errors)));
        }

        if (best == null) {
            throw new AiUnavailableException(
                    "El modelo no ha devuelto un plan que se pueda leer tras %d intentos. Vuelve a intentarlo."
                            .formatted(attempts));
        }
        return toResponse(project, source, team, start, sprintDays, fixedSprints != null, best, attempts);
    }

    /** Lo que el modelo devolvio ya como plan del servidor: fechas, personas y avisos. */
    private AiPlanResponse toResponse(Project project, String source, PlanTeam team, LocalDate start,
                                      int sprintDays, boolean sprintsFijos, Attempt best, int attempts) {
        List<AiTaskScheduler.Window> ventanas = best.sprints();

        List<PlannedSprint> sprints = new ArrayList<>();
        for (int i = 0; i < ventanas.size(); i++) {
            sprints.add(new PlannedSprint(i + 1, ventanas.get(i).start(), ventanas.get(i).end(),
                    best.parsed().goals().get(i + 1)));
        }

        List<ProposedTask> tasks = best.tasks().stream().map(task -> {
            var persona = team.match(task.assigneeName());
            boolean sprintValido = task.sprint() != null && task.sprint() >= 1 && task.sprint() <= ventanas.size();
            return new ProposedTask(task.title(), task.description(), task.reason(), task.type(), task.epic(),
                    task.sprint(), task.priority(), task.estimatedHours(),
                    // La fecha la pone el servidor: el ultimo dia laborable del sprint.
                    sprintValido ? aiTaskScheduler.lastWorkingDay(ventanas.get(task.sprint() - 1)) : null,
                    persona.map(PlanTeam.Member::userId).orElse(null),
                    persona.map(PlanTeam.Member::name).orElse(task.assigneeName()),
                    task.dependsOnOrEmpty());
        }).toList();

        List<String> warnings = new ArrayList<>(best.parsed().warnings());
        if (!best.errors().isEmpty()) {
            warnings.add("Tras %d %s, el plan sigue sin cumplir estas reglas; revisalo antes de aplicarlo:"
                    .formatted(attempts, attempts == 1 ? "intento" : "intentos"));
            warnings.addAll(best.errors());
        }

        LocalDate fin = ventanas.get(ventanas.size() - 1).end();
        long dias = new AiTaskScheduler.Window(start, fin).days();
        Integer semanas = best.parsed().durationWeeks() != null ? best.parsed().durationWeeks()
                : (int) Math.ceil(dias / 7.0);

        // Solo se ofrece guardar en el workspace lo que difiere de lo que ya tiene,
        // para que la pestana Plan pinte exactamente estos sprints.
        AiPlanResponse.SuggestedSchedule schedule = new AiPlanResponse.SuggestedSchedule(
                start.equals(project.getStartDate()) ? null : start,
                sprintsFijos ? null : fin,
                Objects.equals(sprintDays, project.getSprintDays()) ? null : sprintDays);

        log.info("IA: {} tareas en {} sprints para el proyecto {} ({} intentos, {} avisos)",
                tasks.size(), sprints.size(), project.getId(), attempts, warnings.size());

        return AiPlanResponse.of(project.getId(), properties.getModel(), source, semanas, sprints, tasks,
                workload(tasks, team), warnings, best.duplicates(), schedule, attempts);
    }

    /** Horas y tareas de cada persona, contadas sobre el plan: no sobre lo que dice el modelo de si mismo. */
    private List<MemberWorkload> workload(List<ProposedTask> tasks, PlanTeam team) {
        return team.members().stream().map(member -> {
            List<ProposedTask> suyas = tasks.stream()
                    .filter(task -> member.userId().equals(task.assigneeId()))
                    .toList();
            BigDecimal horas = suyas.stream()
                    .map(task -> task.estimatedHours() == null ? BigDecimal.ZERO : task.estimatedHours())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new MemberWorkload(member.userId(), member.name(), suyas.size(), horas);
        }).toList();
    }

    /** Sin fecha de entrega: los que diga el modelo, dentro de lo razonable. */
    private int sprintCount(ParsedPlan parsed) {
        int propuestos = parsed.numSprints() != null ? parsed.numSprints()
                : parsed.tasks().stream().map(ProposedTask::sprint).filter(Objects::nonNull)
                        .max(Integer::compare).orElse(1);
        return Math.max(1, Math.min(MAX_SPRINTS, propuestos));
    }

    private String correctionPrompt(List<String> errors) {
        StringBuilder prompt = new StringBuilder("Tu plan tiene estos problemas:\n");
        errors.forEach(error -> prompt.append("- ").append(error).append('\n'));
        prompt.append("\nCorrigelos y devuelve el plan COMPLETO otra vez, con la misma forma JSON. ")
              .append("No cambies lo que ya estaba bien.");
        return prompt.toString();
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
                .min(Comparator.comparingInt(BoardColumn::getPosition))
                .orElseThrow(() -> new BusinessRuleException("El tablero no tiene columnas"));

        User creator = SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null);
        int position = (int) taskRepository.countByBoardColumn_Id(column.getId());

        // Solo se asigna a gente que pertenezca al equipo del proyecto.
        Set<Long> allowed = project.getTeam().getMembers().stream()
                .map(member -> member.getUser().getId())
                .collect(Collectors.toSet());

        List<Task> created = new ArrayList<>();
        for (ProposedTask proposed : request.tasks()) {
            User assignee = proposed.assigneeId() != null && allowed.contains(proposed.assigneeId())
                    ? userRepository.findById(proposed.assigneeId()).orElse(null)
                    : null;

            created.add(taskRepository.save(Task.builder()
                    .title(proposed.title().trim())
                    .description(descripcion(proposed))
                    .type(proposed.type() == null ? TaskType.TAREA : proposed.type())
                    .epic(proposed.epic() == null || proposed.epic().isBlank() ? null : proposed.epic().strip())
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

    /** Lo que se hace y de donde sale, en la descripcion de la tarea del tablero. */
    private String descripcion(ProposedTask proposed) {
        boolean hayDescripcion = proposed.description() != null && !proposed.description().isBlank();
        boolean hayMotivo = proposed.reason() != null && !proposed.reason().isBlank();
        if (hayDescripcion && hayMotivo) return proposed.description().strip() + "\n\nMotivo: " + proposed.reason().strip();
        if (hayDescripcion) return proposed.description().strip();
        return hayMotivo ? proposed.reason().strip() : null;
    }

    private List<Task> existingTasks(Project project) {
        if (project.getBoards().isEmpty()) return List.of();
        return taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(project.getBoards().get(0).getId());
    }

    /**
     * Al modelo se le dice que no repita tareas, pero un modelo pequeno lo ignora a
     * menudo: la comprobacion se hace tambien aqui, que es determinista.
     */
    private List<ProposedTask> withoutDuplicates(List<ProposedTask> proposed, List<Task> existing) {
        List<String> known = existing.stream().map(task -> TextNormalizer.keywords(task.getTitle())).toList();

        List<ProposedTask> result = new ArrayList<>();
        for (ProposedTask task : proposed) {
            String candidate = TextNormalizer.keywords(task.title());
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

    private String recortar(String indications) {
        if (indications == null || indications.isBlank()) return null;
        String limpio = indications.strip();
        return limpio.length() > MAX_INDICATIONS_LENGTH ? limpio.substring(0, MAX_INDICATIONS_LENGTH) : limpio;
    }

    /**
     * El mensaje de usuario: todo lo que cambia de una ejecucion a otra, por
     * apartados, para que el modelo distinga el calendario, el equipo, lo pedido
     * solo para esta vez y el documento.
     */
    String buildUserPrompt(String text, PlanTeam team, LocalDate start, int sprintDays,
                           List<AiTaskScheduler.Window> fixedSprints, String indications, List<Task> existing) {
        StringBuilder prompt = new StringBuilder();
        // Sin esto el modelo planifica en el ano en que se entreno.
        prompt.append("Fecha de hoy: ").append(LocalDate.now()).append('\n');
        prompt.append("Fecha de inicio: ").append(start).append('\n');
        prompt.append("Duracion del sprint: ").append(sprintDays).append(" dias")
              .append(sprintDays % 7 == 0 ? " (" + sprintDays / 7 + (sprintDays == 7 ? " semana)" : " semanas)") : "")
              .append('\n');

        prompt.append("\nSPRINTS\n");
        if (fixedSprints != null) {
            prompt.append("Son fijos: usa exactamente estos numeros y fechas, no crees otros.\n");
            for (int i = 0; i < fixedSprints.size(); i++) {
                AiTaskScheduler.Window sprint = fixedSprints.get(i);
                prompt.append("- Sprint ").append(i + 1).append(": ").append(sprint.start())
                      .append(" a ").append(sprint.end()).append(" · capacidad: ")
                      .append(team.members().stream()
                              .map(member -> member.name() + " " + plano(member.capacityFor(sprint.days())) + " h")
                              .collect(Collectors.joining(", ")))
                      .append('\n');
            }
        } else {
            prompt.append("No hay fecha de entrega fijada: pon en \"resumen.num_sprints\" cuantos hacen falta ")
                  .append("(entre 1 y ").append(MAX_SPRINTS).append("). Empiezan el ").append(start)
                  .append(" y cada uno dura ").append(sprintDays).append(" dias.\n");
        }

        prompt.append("\nEQUIPO (").append(team.members().size())
              .append(team.members().size() == 1 ? " persona)\n" : " personas)\n");
        for (PlanTeam.Member member : team.members()) {
            prompt.append("- ").append(member.name());
            if (member.leader()) prompt.append(" (lider)");
            prompt.append(" | ").append(member.hoursPerWeek()).append(" h/semana");
            if (fixedSprints == null) {
                prompt.append(" (capacidad por sprint: ").append(plano(member.capacityFor(sprintDays))).append(" h)");
            }
            prompt.append(" | preferencias: ")
                  .append(member.preferences().isBlank() ? "ninguna" : member.preferences().replace('\n', ' '))
                  .append('\n');
        }

        if (indications != null) {
            prompt.append("\nINDICACIONES ADICIONALES (solo para este plan)\n").append(indications).append('\n');
        }

        if (!existing.isEmpty()) {
            prompt.append("\nYA ESTA EN EL TABLERO (no lo repitas)\n");
            existing.forEach(task -> prompt.append("- ").append(task.getTitle()).append('\n'));
        }

        prompt.append("\nDOCUMENTO DEL PROYECTO\n").append(text).append('\n');
        return prompt.toString();
    }

    private static String plano(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    // ---------------------------------------------------------------------
    // Interpretar la respuesta
    // ---------------------------------------------------------------------

    /** Lo que se pudo leer de una respuesta, todavia sin fechas ni personas resueltas. */
    record ParsedPlan(List<ProposedTask> tasks, Integer durationWeeks, Integer numSprints,
                      Map<Integer, String> goals, List<String> warnings) {
    }

    /** La respuesta no es un plan: se le devuelve al modelo como error a corregir. */
    static class InvalidPlanException extends RuntimeException {
        InvalidPlanException(String message) {
            super(message);
        }
    }

    /**
     * Lee el JSON del modelo. Se aceptan las claves en espanol que pide el prompt y
     * sus equivalentes en ingles, porque un modelo pequeno las cambia a veces; lo
     * que no se acepta es algo sin lista de tareas.
     */
    ParsedPlan parse(String content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content == null ? "" : content);
        } catch (Exception ex) {
            throw new InvalidPlanException("La respuesta no era JSON valido. Responde solo con el objeto JSON.");
        }
        JsonNode array = root == null ? null : root.isArray() ? root : first(root, "tareas", "tasks");
        if (array == null || !array.isArray()) {
            throw new InvalidPlanException("La respuesta no tenia la lista \"tareas\". Usa exactamente la forma pedida.");
        }

        List<ProposedTask> tasks = new ArrayList<>();
        for (JsonNode node : array) {
            String title = text(node, 200, "titulo", "title");
            if (title == null) continue;

            tasks.add(new ProposedTask(
                    title,
                    text(node, 2000, "descripcion", "description"),
                    text(node, 500, "motivo", "reason"),
                    type(text(node, 40, "tipo", "type")),
                    text(node, 100, "epica", "epic"),
                    integer(first(node, "sprint")),
                    priority(text(node, 20, "prioridad", "priority")),
                    hours(first(node, "horas", "estimatedHours", "hours")),
                    null,
                    null,
                    text(node, 150, "asignado", "assignee"),
                    names(first(node, "depende_de", "dependsOn"))));

            if (tasks.size() >= properties.getMaxTasks()) break;
        }

        JsonNode resumen = root.isArray() ? null : first(root, "resumen", "summary");
        Map<Integer, String> goals = new HashMap<>();
        JsonNode sprints = root.isArray() ? null : first(root, "sprints");
        if (sprints != null && sprints.isArray()) {
            for (JsonNode sprint : sprints) {
                Integer numero = integer(first(sprint, "numero", "number"));
                String objetivo = text(sprint, 300, "objetivo", "goal");
                if (numero != null && objetivo != null) goals.put(numero, objetivo);
            }
        }

        return new ParsedPlan(
                tasks,
                resumen == null ? null : integer(first(resumen, "duracion_semanas", "durationWeeks")),
                resumen == null ? null : integer(first(resumen, "num_sprints", "numSprints")),
                goals,
                root.isArray() ? List.of() : names(first(root, "avisos", "warnings")));
    }

    /** El primer campo presente de los que se le pasan, o null. */
    private static JsonNode first(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.isMissingNode()) return value;
        }
        return null;
    }

    private static String text(JsonNode node, int max, String... fields) {
        JsonNode value = first(node, fields);
        if (value == null || value.isContainerNode()) return null;
        String limpio = value.asText("").strip();
        if (limpio.isBlank()) return null;
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }

    /** Un numero, venga como numero o como texto ("Sprint 2", "4h"). */
    private static Integer integer(JsonNode value) {
        if (value == null) return null;
        if (value.isNumber()) return value.asInt();
        String digitos = value.asText("").replaceAll("[^0-9]", "");
        if (digitos.isEmpty() || digitos.length() > 6) return null;
        return Integer.parseInt(digitos);
    }

    private static BigDecimal hours(JsonNode value) {
        if (value == null) return null;
        double horas;
        if (value.isNumber()) {
            horas = value.asDouble();
        } else {
            String limpio = value.asText("").replace(',', '.').replaceAll("[^0-9.]", "");
            try {
                horas = limpio.isEmpty() ? 0 : Double.parseDouble(limpio);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (horas <= 0 || horas > 100) return null;
        return BigDecimal.valueOf(horas).stripTrailingZeros();
    }

    /** "Alta", "ALTA" o "alta" son lo mismo. Lo que no se entiende, media. */
    private static TaskPriority priority(String raw) {
        String valor = TextNormalizer.plain(raw);
        if (valor.startsWith("crit")) return TaskPriority.CRITICA;
        if (valor.startsWith("alt") || valor.startsWith("high")) return TaskPriority.ALTA;
        if (valor.startsWith("baj") || valor.startsWith("low")) return TaskPriority.BAJA;
        return TaskPriority.MEDIA;
    }

    /** "Historia de usuario", "Tarea", "Bug"... Si no se entiende, se deja en blanco: se lee como TAREA. */
    private static TaskType type(String raw) {
        String valor = TextNormalizer.plain(raw);
        if (valor.startsWith("historia") || valor.startsWith("story")) return TaskType.HISTORIA;
        if (valor.startsWith("tarea") || valor.startsWith("task")) return TaskType.TAREA;
        if (valor.startsWith("bug")) return TaskType.BUG;
        if (valor.startsWith("epica") || valor.startsWith("epic")) return TaskType.EPICA;
        if (valor.startsWith("incidencia")) return TaskType.INCIDENCIA;
        return null;
    }

    /** Una lista de textos del modelo, sin los vacios. */
    private static List<String> names(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> names = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.isTextual() ? item.asText("").strip() : item.toString();
            if (!value.isBlank()) names.add(value);
        }
        return names;
    }

    private static String abbreviate(String value) {
        if (value == null) return "";
        return value.length() <= 300 ? value : value.substring(0, 300) + "…";
    }
}
