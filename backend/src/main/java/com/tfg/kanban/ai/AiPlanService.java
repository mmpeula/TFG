package com.tfg.kanban.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.ai.dto.AiPlanResponse;
import com.tfg.kanban.ai.dto.ApplyPlanRequest;
import com.tfg.kanban.ai.dto.MemberWorkload;
import com.tfg.kanban.ai.dto.PlanOptions;
import com.tfg.kanban.ai.dto.PlannedSprint;
import com.tfg.kanban.ai.document.DocumentIngestionService;
import com.tfg.kanban.ai.document.ProcessedDocument;
import com.tfg.kanban.ai.dto.ProposedTask;
import com.tfg.kanban.board.BoardService;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.TaskRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.project.ProjectService;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.task.TaskMapper;
import com.tfg.kanban.task.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Planificacion automatica a partir del documento de la practica.
 *
 * <p>El modelo <b>propone</b>: nada se escribe en el tablero hasta que el usuario
 * acepta las tareas que quiere ({@link #apply}). Es la regla de producto del modulo
 * de IA y la razon de separar generar y aplicar en dos endpoints.</p>
 *
 * <p>Una sola llamada a DeepSeek con el documento entero (el procesamiento de
 * {@link DocumentIngestionService} lo acota por secciones, como red de
 * seguridad), y de ella sale el reparto completo de todos los sprints de una
 * vez. Lo que si es medible —el reparto de horas, las dependencias entre
 * tareas— se comprueba en Java sobre el resultado, no se le pide al modelo que
 * se autoevalue. Las fechas de los sprints y de las tareas las pone siempre el
 * servidor, para que casen con los sprints de la pestana Plan.</p>
 *
 * <p>Generar no va en una transaccion: lo que hace falta de la base de datos se lee
 * antes, en una corta ({@link PlanInput}), y la llamada al modelo, que puede tardar
 * minutos, va fuera. Si no, cada generacion tendria una conexion del pool esperando
 * a DeepSeek, y unas pocas a la vez dejarian sin conexiones al resto de la aplicacion.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlanService {

    /**
     * El texto vive en {@code src/main/resources/prompts/plan-system.md}, no aqui:
     * es contenido editorial (como se reparte, que formato de salida), no logica,
     * y asi se puede retocar sin tocar codigo Java. Las cuentas (capacidad util,
     * margen) ya van hechas en el mensaje de usuario; las fechas las pone siempre
     * el servidor, aunque el JSON pedido tambien las lleve.
     */
    static final String SYSTEM_PROMPT = loadSystemPrompt();

    private static String loadSystemPrompt() {
        try (var in = new ClassPathResource("prompts/plan-system.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se ha podido cargar prompts/plan-system.md", ex);
        }
    }

    /** Sin fecha de entrega, cuantos sprints se aceptan como mucho. */
    private static final int MAX_SPRINTS = 12;

    /**
     * Cuantas veces se le pide al modelo que corrija el reparto (horas y
     * criticas por persona, autovalidacion) antes de dejarlo y ajustarlo en
     * codigo con {@link #rebalance}. Es un reintento aparte del de JSON invalido
     * de {@link #requestPlan}: aqui el JSON ya se ha podido leer, lo que falla
     * es una regla medible del reparto.
     */
    private static final int MAX_BALANCE_RETRIES = 2;

    /** Cuantos movimientos como mucho intenta {@link #rebalance} por regla, para no darle vueltas sin fin. */
    private static final int MAX_REBALANCE_MOVES = 20;

    private final AiProperties properties;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final BoardService boardService;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final TaskMapper taskMapper;
    private final DocumentIngestionService documentIngestion;
    private final PlanProgressTracker progressTracker;
    private final AiTaskScheduler aiTaskScheduler;
    private final TransactionOperations transactions;

    /** Genera el plan a partir del guion ya guardado en el proyecto. */
    public AiPlanResponse generate(Long projectId, PlanOptions options) {
        ensureEnabled();
        return tracked(projectId, () -> plan(transactions.execute(tx -> {
            Project project = projectService.getProject(projectId);
            if (project.getBriefing() == null || project.getBriefing().isBlank()) {
                throw new BusinessRuleException(
                        "El proyecto no tiene guion de la práctica: súbelo como documento o escríbelo en el proyecto");
            }
            return prepare(project, project.getBriefing(), "guion del proyecto", options, List.of());
        })));
    }

    /**
     * Genera el plan a partir de un documento subido. Si sale bien, el texto extraido se
     * guarda ademas como guion del proyecto: es la fuente de verdad de la practica.
     */
    public AiPlanResponse generateFromDocument(Long projectId, MultipartFile file, PlanOptions options) {
        return tracked(projectId, () -> {
            // Leer el documento tambien puede llamar a DeepSeek (paginas escaneadas): fuera de la transaccion.
            // Va antes de mirar si la IA esta activa: un documento que no se puede leer se dice primero.
            ProcessedDocument document = documentIngestion.ingest(file, stage -> progressTracker.advance(projectId, stage));
            ensureEnabled();
            String context = document.context();
            AiPlanResponse response = plan(transactions.execute(tx -> prepare(projectService.getProject(projectId),
                    context, "documento: " + document.filename(), options, document.warnings())));

            transactions.executeWithoutResult(tx -> projectService.getProject(projectId).setBriefing(context));
            return response;
        });
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new AiUnavailableException("El módulo de IA está desactivado en la configuración del servidor");
        }
    }

    /**
     * Mientras dura la generacion, la interfaz puede preguntar por que fase va
     * ({@link PlanProgressTracker}); al terminar, bien o mal, se olvida.
     */
    private <T> T tracked(Long projectId, Supplier<T> generation) {
        progressTracker.advance(projectId, ProcessingStage.RECEIVED);
        try {
            return generation.get();
        } finally {
            progressTracker.clear(projectId);
        }
    }

    /**
     * Busca la fecha de inicio en un documento sin generar nada: es deteccion por
     * texto, no una llamada a DeepSeek, asi que se puede pedir antes de generar para
     * no tener que adivinar (y luego regenerar si la adivinanza fallaba). Tampoco toca
     * la base de datos, asi que no abre transaccion.
     */
    public Optional<LocalDate> detectStartDate(MultipartFile file) {
        return DocumentDateDetector.detect(documentIngestion.localContext(file));
    }

    /**
     * Genera el plan a partir de un texto escrito a mano («tenemos que hacer una
     * practica de...»). No toca el guion guardado: eso queda reservado al
     * documento oficial de la actividad.
     */
    public AiPlanResponse generateFromText(Long projectId, String text, PlanOptions options) {
        if (text == null || text.isBlank()) {
            throw new BusinessRuleException("Escribe de qué va el trabajo para que pueda proponerte tareas");
        }
        ensureEnabled();
        return tracked(projectId, () -> plan(transactions.execute(tx ->
                prepare(projectService.getProject(projectId), text.trim(), "descripción escrita", options, List.of()))));
    }

    /**
     * Todo lo que la generacion necesita de la base de datos, ya leido: el equipo, el
     * calendario, las tareas que ya hay en el tablero y el prompt montado (con el ritmo
     * historico de cada persona). Con esto la llamada al modelo ya no toca la base de datos.
     */
    private record PlanInput(Long projectId, String text, String source, PlanTeam team, LocalDate start,
                             int sprintDays, List<AiTaskScheduler.Window> fixedWindows,
                             List<String> existingTitles, String userPrompt, List<String> documentWarnings) {
    }

    /** Lee y resuelve lo que hace falta para generar. Se llama dentro de una transaccion corta. */
    private PlanInput prepare(Project project, String text, String source, PlanOptions requested,
                              List<String> documentWarnings) {
        PlanOptions options = requested == null ? PlanOptions.defaults() : requested;

        PlanTeam team = PlanTeam.resolve(project.getTeam().getMembers(), options.team());
        // Solo se busca en el documento cuando de verdad hace falta: si ya hay una
        // fecha del formulario o del workspace, esa manda y no compensa leer el texto.
        Optional<LocalDate> fromDocument = options.startDate() == null && project.getStartDate() == null
                ? DocumentDateDetector.detect(text) : Optional.empty();
        LocalDate start = options.startDate() != null ? options.startDate()
                : project.getStartDate() != null ? project.getStartDate()
                : fromDocument.orElse(LocalDate.now());
        int sprintDays = AiTaskScheduler.daysOrDefault(
                options.sprintDays() != null ? options.sprintDays() : project.getSprintDays());

        // Con fecha de entrega los sprints salen de ahi y el modelo solo los rellena;
        // sin ella, cuantos hacen falta lo propone el modelo. La del formulario manda
        // sobre la guardada, igual que con la fecha de inicio.
        LocalDate deadline = options.deadline() != null ? options.deadline() : project.getDeadline();
        boolean sprintsFijos = deadline != null && deadline.isAfter(start);
        List<AiTaskScheduler.Window> fixedWindows = sprintsFijos
                ? aiTaskScheduler.sprintsOf(new AiTaskScheduler.Window(start, deadline), sprintDays)
                : null;

        List<String> existingTitles = existingTasks(project).stream().map(Task::getTitle).toList();
        String userPrompt = buildPrompt(text, team, start, sprintDays, fixedWindows, existingTitles);
        return new PlanInput(project.getId(), text, source, team, start, sprintDays, fixedWindows,
                existingTitles, userPrompt, documentWarnings);
    }

    /** Pide el plan al modelo y lo completa. No toca la base de datos: todo viene ya en {@code input}. */
    private AiPlanResponse plan(PlanInput input) {
        progressTracker.advance(input.projectId(), ProcessingStage.PLANNING);
        BalancedPlan balanced = requestBalancedPlan(input.userPrompt(), input.team());
        ParsedPlan parsed = balanced.parsed();

        List<AiTaskScheduler.Window> ventanas = input.fixedWindows() != null ? input.fixedWindows()
                : aiTaskScheduler.consecutiveSprints(input.start(), sprintCountFrom(parsed), input.sprintDays());

        List<String> deliveryWarnings = IterationDeliveryDetector.detect(input.text()).stream()
                .map(IterationDeliveryDetector.Delivery::describe).toList();

        AiPlanResponse response = toResponse(input, ventanas, parsed, deliveryWarnings);
        List<String> extra = new ArrayList<>(input.documentWarnings());
        extra.addAll(balanced.warnings());
        return extra.isEmpty() ? response : withExtraWarnings(response, extra);
    }

    /** La misma respuesta con avisos de mas al final: los que anade el ajuste de reparto, fuera de {@code toResponse}. */
    private static AiPlanResponse withExtraWarnings(AiPlanResponse response, List<String> extra) {
        List<String> combinados = new ArrayList<>(response.warnings());
        combinados.addAll(extra);
        return new AiPlanResponse(response.status(), response.projectId(), response.model(), response.source(),
                response.message(), response.durationWeeks(), response.sprints(), response.tasks(),
                response.workload(), combinados, response.generatedAt());
    }

    /** El JSON tal cual lo ha devuelto el modelo, junto con lo que se ha podido interpretar de el. */
    private record PlanAttempt(String raw, ParsedPlan parsed) {
    }

    /**
     * Una sola llamada al modelo, con todo: de ella sale el reparto completo de
     * todos los sprints. Si el JSON no se puede interpretar, se reintenta una
     * unica vez, pasandole su propia respuesta y el error de parseo para que la
     * corrija; si tampoco esa vez es legible, se da por perdido.
     */
    private PlanAttempt requestPlan(String userPrompt) {
        String raw = deepSeekClient.chatJson(SYSTEM_PROMPT, userPrompt);
        try {
            return new PlanAttempt(raw, parse(raw));
        } catch (InvalidPlanException primerError) {
            log.warn("IA: respuesta no interpretable, se pide una correccion: {}", abbreviate(raw));
            String corregido = deepSeekClient.chatJson(SYSTEM_PROMPT, List.of(
                    new DeepSeekClient.Message("user", userPrompt),
                    new DeepSeekClient.Message("assistant", raw),
                    new DeepSeekClient.Message("user", ("Eso no es un JSON valido: %s Corrigelo y responde solo con "
                            + "el JSON pedido, sin texto antes ni despues.").formatted(primerError.getMessage()))));
            try {
                return new PlanAttempt(corregido, parse(corregido));
            } catch (InvalidPlanException segundoError) {
                log.warn("IA: la correccion tampoco es interpretable: {}", abbreviate(corregido));
                throw new AiUnavailableException(
                        "El modelo no ha devuelto un plan que se pueda leer, ni siquiera tras pedirle que lo corrija. "
                                + "Vuelve a intentarlo.");
            }
        }
    }

    /**
     * El plan ya interpretado, mas los avisos del ajuste de reparto (vacia si no
     * ha hecho falta ninguno). Van aparte de {@link ParsedPlan#modelWarnings()} a
     * proposito, para no mezclar lo que dice el modelo con lo que corrige el codigo.
     */
    private record BalancedPlan(ParsedPlan parsed, List<String> warnings) {
    }

    /**
     * Igual que {@link #requestPlan}, pero ademas comprueba el reparto con
     * {@link PlanBalanceValidator}: horas y tareas criticas por persona, y que
     * quien implementa no sea el unico que valida. Si algo falla, se le manda al
     * modelo su propio JSON y la lista exacta de lo que incumple para que lo
     * corrija, hasta {@link #MAX_BALANCE_RETRIES} veces. Si sigue sin cumplir,
     * {@link #rebalance} hace un ultimo ajuste en codigo, sin volver a llamar al
     * modelo.
     */
    private BalancedPlan requestBalancedPlan(String userPrompt, PlanTeam team) {
        PlanAttempt intento = requestPlan(userPrompt);

        for (int vez = 1; vez <= MAX_BALANCE_RETRIES; vez++) {
            List<PlanBalanceValidator.Violation> violaciones = PlanBalanceValidator.check(intento.parsed().tasks(), team);
            if (violaciones.isEmpty()) return new BalancedPlan(intento.parsed(), List.of());

            log.warn("IA: reparto desequilibrado, correccion {}/{}: {}", vez, MAX_BALANCE_RETRIES, violaciones);
            String textoViolaciones = violaciones.stream().map(PlanBalanceValidator.Violation::message)
                    .collect(Collectors.joining(" "));
            String mensajeCorreccion = ("El reparto de responsables no cumple estas reglas: %s "
                    + "Corrige solo quien es el responsable de cada tarea, sin cambiar titulos, horas, "
                    + "prioridades ni sprints, y responde otra vez con el JSON completo.").formatted(textoViolaciones);
            String correccion = deepSeekClient.chatJson(SYSTEM_PROMPT, List.of(
                    new DeepSeekClient.Message("user", userPrompt),
                    new DeepSeekClient.Message("assistant", intento.raw()),
                    new DeepSeekClient.Message("user", mensajeCorreccion)));
            try {
                intento = new PlanAttempt(correccion, parse(correccion));
            } catch (InvalidPlanException ex) {
                log.warn("IA: la correccion de reparto no es interpretable, se deja como esta: {}",
                        abbreviate(correccion));
                break;
            }
        }

        List<PlanBalanceValidator.Violation> pendientes = PlanBalanceValidator.check(intento.parsed().tasks(), team);
        return pendientes.isEmpty() ? new BalancedPlan(intento.parsed(), List.of())
                : rebalance(intento.parsed(), team, pendientes);
    }

    /**
     * El ultimo ajuste, sin llamar al modelo: mueve una tarea cada vez de quien
     * mas horas o mas criticas tiene a quien menos, y solo si el movimiento
     * mejora de verdad la diferencia (nunca la empeora ni la deja igual). Con
     * tareas muy grandes frente al hueco a cubrir puede que no haya ningun
     * movimiento que ayude; en ese caso no se mueve nada y se avisa.
     *
     * <p>Solo se tocan las dos reglas medibles por numero (horas, criticas). La
     * autovalidacion y la cobertura por sprint no se arreglan moviendo una tarea
     * suelta sin criterio, asi que lo que quede de esas se deja como aviso.</p>
     */
    private BalancedPlan rebalance(ParsedPlan parsed, PlanTeam team, List<PlanBalanceValidator.Violation> iniciales) {
        List<ProposedTask> tareas = new ArrayList<>(parsed.tasks());
        Set<String> referenciadas = tareas.stream()
                .flatMap(task -> task.dependsOnOrEmpty().stream())
                .map(TextNormalizer::plain)
                .collect(Collectors.toSet());

        log.warn("IA: el modelo no ha corregido el reparto, se ajusta en codigo: {}", iniciales);

        for (int mov = 0; mov < MAX_REBALANCE_MOVES && moverPorHoras(tareas, team, referenciadas); mov++) {
            // el cuerpo esta en la condicion: cada vuelta mueve como mucho una tarea
        }
        for (int mov = 0; mov < MAX_REBALANCE_MOVES && moverPorCriticas(tareas, team, referenciadas); mov++) {
            // idem, para las tareas criticas
        }

        List<PlanBalanceValidator.Violation> restantes = PlanBalanceValidator.check(tareas, team);
        List<String> avisos = restantes.isEmpty() ? List.of()
                : List.of("El reparto no ha quedado del todo equilibrado, ni siquiera tras el ajuste automático: %s"
                        .formatted(restantes.stream().map(PlanBalanceValidator.Violation::message)
                                .collect(Collectors.joining(" "))));
        ParsedPlan resultado = new ParsedPlan(tareas, parsed.numSprints(), parsed.goals(), parsed.modelWarnings());
        return new BalancedPlan(resultado, avisos);
    }

    /**
     * Busca, entre las tareas de quien mas ocupa su disponibilidad, la que al
     * moverla a otra persona reduzca mas esa diferencia, y la mueve. Solo se
     * consideran tareas con un unico responsable y que ninguna otra tarea
     * necesite como dependencia (moverlas no cambia el orden del sprint).
     */
    private static boolean moverPorHoras(List<ProposedTask> tareas, PlanTeam team, Set<String> referenciadas) {
        Map<PlanTeam.Member, BigDecimal> horas = PlanBalanceValidator.hoursByMember(tareas, team);
        if (horas.size() < 2 || PlanBalanceValidator.hoursGap(horas).compareTo(PlanBalanceValidator.MAX_HOURS_GAP) <= 0) {
            return false;
        }
        PlanTeam.Member masCargado = horas.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElseThrow();

        int mejorIndice = -1;
        PlanTeam.Member mejorDestino = null;
        BigDecimal mejorGap = PlanBalanceValidator.hoursGap(horas);

        for (int i = 0; i < tareas.size(); i++) {
            ProposedTask tarea = tareas.get(i);
            if (tarea.estimatedHours() == null || referenciadas.contains(TextNormalizer.plain(tarea.title()))) continue;
            List<PlanTeam.Member> asignados = team.matchAll(tarea.assigneeNamesOrEmpty());
            if (asignados.size() != 1 || !asignados.get(0).equals(masCargado)) continue;

            for (PlanTeam.Member destino : horas.keySet()) {
                if (destino.equals(masCargado)) continue;
                Map<PlanTeam.Member, BigDecimal> simuladas = new LinkedHashMap<>(horas);
                simuladas.merge(masCargado, tarea.estimatedHours().negate(), BigDecimal::add);
                simuladas.merge(destino, tarea.estimatedHours(), BigDecimal::add);
                BigDecimal gapSimulado = PlanBalanceValidator.hoursGap(simuladas);
                if (gapSimulado.compareTo(mejorGap) < 0) {
                    mejorGap = gapSimulado;
                    mejorIndice = i;
                    mejorDestino = destino;
                }
            }
        }

        if (mejorIndice < 0) return false;
        tareas.set(mejorIndice, withResponsable(tareas.get(mejorIndice), mejorDestino.name()));
        return true;
    }

    /**
     * Igual que {@link #moverPorHoras} pero con tareas criticas: mueve una de
     * quien pasa del tope hacia quien menos criticas tiene, si con eso no lo
     * hace pasarse a el.
     */
    private static boolean moverPorCriticas(List<ProposedTask> tareas, PlanTeam team, Set<String> referenciadas) {
        Map<PlanTeam.Member, Long> criticas = PlanBalanceValidator.criticalCountByMember(tareas, team);
        long total = criticas.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0 || criticas.size() < 2) return false;
        long limite = PlanBalanceValidator.limiteCriticas(total, criticas.size());

        Map.Entry<PlanTeam.Member, Long> origen = criticas.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElseThrow();
        if (origen.getValue() <= limite) return false;
        Map.Entry<PlanTeam.Member, Long> destino = criticas.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(origen.getKey()))
                .min(Map.Entry.comparingByValue()).orElse(null);
        if (destino == null || destino.getValue() + 1 > limite) return false;

        for (int i = 0; i < tareas.size(); i++) {
            ProposedTask tarea = tareas.get(i);
            if (tarea.priority() != TaskPriority.CRITICA
                    || referenciadas.contains(TextNormalizer.plain(tarea.title()))) {
                continue;
            }
            List<PlanTeam.Member> asignados = team.matchAll(tarea.assigneeNamesOrEmpty());
            if (asignados.size() != 1 || !asignados.get(0).equals(origen.getKey())) continue;

            tareas.set(i, withResponsable(tarea, destino.getKey().name()));
            return true;
        }
        return false;
    }

    /** La misma tarea con otro unico responsable; el resto (horas, prioridad, dependencias...) no cambia. */
    private static ProposedTask withResponsable(ProposedTask task, String nombre) {
        return new ProposedTask(task.title(), task.description(), task.reason(), task.type(), task.epic(),
                task.sprint(), task.priority(), task.estimatedHours(), task.dueDate(), List.of(), List.of(nombre),
                task.dependsOnOrEmpty(), task.labelsOrEmpty());
    }

    /** Sin fecha de entrega: los que diga el modelo, dentro de lo razonable. */
    private int sprintCountFrom(ParsedPlan parsed) {
        int propuestos = parsed.numSprints() != null ? parsed.numSprints()
                : parsed.tasks().stream().map(ProposedTask::sprint).filter(Objects::nonNull)
                        .max(Integer::compare).orElse(1);
        return Math.max(1, Math.min(MAX_SPRINTS, propuestos));
    }

    /** Junta lo que ha devuelto el modelo, resuelve fechas y personas, y calcula los avisos. */
    private AiPlanResponse toResponse(PlanInput input, List<AiTaskScheduler.Window> ventanas, ParsedPlan parsed,
                                      List<String> deliveryWarnings) {
        PlanTeam team = input.team();

        // Un sprint que no exista se trata como "sin sprint", no se descarta la tarea.
        List<ProposedTask> conSprintValido = parsed.tasks().stream()
                .map(task -> sprintValido(task.sprint(), ventanas.size()) ? task : withSprint(task, null))
                .toList();

        List<ProposedTask> sinDuplicados = withoutDuplicates(conSprintValido, input.existingTitles());
        int duplicates = conSprintValido.size() - sinDuplicados.size();
        List<ProposedTask> limitadas = sinDuplicados.size() > properties.getMaxTasks()
                ? sinDuplicados.subList(0, properties.getMaxTasks()) : sinDuplicados;

        List<ProposedTask> tasks = limitadas.stream().map(task -> {
            List<PlanTeam.Member> personas = team.matchAll(task.assigneeNamesOrEmpty());
            List<Long> ids = personas.stream().map(PlanTeam.Member::userId).toList();
            List<String> nombres = task.assigneeNamesOrEmpty().stream()
                    .map(nombre -> team.match(nombre).map(PlanTeam.Member::name).orElse(nombre))
                    .toList();
            boolean sprintValido = sprintValido(task.sprint(), ventanas.size());
            return new ProposedTask(task.title(), task.description(), task.reason(), task.type(), task.epic(),
                    task.sprint(), task.priority(), task.estimatedHours(),
                    // La fecha la pone el servidor: el ultimo dia laborable del sprint.
                    sprintValido ? aiTaskScheduler.lastWorkingDay(ventanas.get(task.sprint() - 1)) : null,
                    ids, nombres, task.dependsOnOrEmpty(), task.labelsOrEmpty());
        }).toList();

        DependencyCheck depCheck = validateDependencies(tasks);
        tasks = depCheck.tasks();

        List<String> warnings = new ArrayList<>();
        warnings.addAll(pendingWarnings(tasks));
        warnings.addAll(depCheck.warnings());
        warnings.addAll(parsed.modelWarnings());
        warnings.addAll(deliveryWarnings);

        List<PlannedSprint> sprints = new ArrayList<>();
        for (int i = 0; i < ventanas.size(); i++) {
            ParsedPlan.SprintGoal goal = parsed.goals().get(i + 1);
            sprints.add(new PlannedSprint(i + 1, ventanas.get(i).start(), ventanas.get(i).end(),
                    goal == null ? null : goal.objetivo()));
        }

        LocalDate fin = ventanas.get(ventanas.size() - 1).end();
        long dias = new AiTaskScheduler.Window(input.start(), fin).days();
        int semanas = (int) Math.ceil(dias / 7.0);

        log.info("IA: {} tareas en {} sprints para el proyecto {} ({} avisos)",
                tasks.size(), sprints.size(), input.projectId(), warnings.size());

        return AiPlanResponse.of(input.projectId(), properties.getModel(), input.source(), semanas, sprints, tasks,
                workload(tasks, team), warnings, duplicates);
    }

    private static boolean sprintValido(Integer sprint, int totalSprints) {
        return sprint != null && sprint >= 1 && sprint <= totalSprints;
    }

    private static ProposedTask withSprint(ProposedTask task, Integer sprint) {
        return new ProposedTask(task.title(), task.description(), task.reason(), task.type(), task.epic(),
                sprint, task.priority(), task.estimatedHours(), task.dueDate(), task.assigneeIds(),
                task.assigneeNames(), task.dependsOnOrEmpty(), task.labelsOrEmpty());
    }

    /** Lo que queda de comprobar las dependencias: las tareas con solo las validas, y un aviso por cada una descartada. */
    private record DependencyCheck(List<ProposedTask> tasks, List<String> warnings) {
    }

    /**
     * Una dependencia solo vale si apunta a una tarea que existe de verdad, que no
     * es ella misma, y que esta en su mismo sprint o en uno anterior. El modelo ya
     * lo tiene entre sus reglas, pero es justo el tipo de cosa medible que no se
     * deja a su palabra: si se equivoca, se descarta esa dependencia concreta y se
     * avisa, sin tirar el resto del plan.
     */
    private DependencyCheck validateDependencies(List<ProposedTask> tasks) {
        Map<String, ProposedTask> porTitulo = new LinkedHashMap<>();
        for (ProposedTask task : tasks) {
            porTitulo.putIfAbsent(TextNormalizer.plain(task.title()), task);
        }

        List<String> warnings = new ArrayList<>();
        List<ProposedTask> resultado = new ArrayList<>();
        for (ProposedTask task : tasks) {
            List<String> validas = new ArrayList<>();
            for (String titulo : task.dependsOnOrEmpty()) {
                ProposedTask previa = porTitulo.get(TextNormalizer.plain(titulo));
                boolean valida = previa != null && previa != task
                        && task.sprint() != null && previa.sprint() != null && previa.sprint() <= task.sprint();
                if (valida) {
                    validas.add(previa.title());
                } else {
                    warnings.add("Se ha descartado una dependencia de «%s» hacia «%s»: no es válida (no existe, es ella misma o está en un sprint posterior)."
                            .formatted(task.title(), titulo));
                }
            }
            resultado.add(validas.size() == task.dependsOnOrEmpty().size() ? task : withDependsOn(task, validas));
        }
        return new DependencyCheck(resultado, warnings);
    }

    private static ProposedTask withDependsOn(ProposedTask task, List<String> dependsOn) {
        return new ProposedTask(task.title(), task.description(), task.reason(), task.type(), task.epic(),
                task.sprint(), task.priority(), task.estimatedHours(), task.dueDate(), task.assigneeIds(),
                task.assigneeNames(), dependsOn, task.labelsOrEmpty());
    }

    /** Las tareas que el modelo ha dejado marcadas como incompletas, para que el usuario las revise. */
    private List<String> pendingWarnings(List<ProposedTask> tasks) {
        return tasks.stream()
                .filter(task -> task.reason() != null && task.reason().toUpperCase(Locale.ROOT).startsWith("PENDIENTE:"))
                .map(task -> "«%s»: %s".formatted(task.title(), task.reason()))
                .toList();
    }

    /** Horas y tareas de cada persona, contadas sobre el plan: no sobre lo que dice el modelo de si mismo. */
    private List<MemberWorkload> workload(List<ProposedTask> tasks, PlanTeam team) {
        return team.members().stream().map(member -> {
            List<ProposedTask> suyas = tasks.stream()
                    .filter(task -> task.assigneeIdsOrEmpty().contains(member.userId()))
                    .toList();
            BigDecimal horas = suyas.stream()
                    .map(task -> task.estimatedHours() == null ? BigDecimal.ZERO : task.estimatedHours())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new MemberWorkload(member.userId(), member.name(), suyas.size(), horas);
        }).toList();
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

        // Solo se asigna a gente que pertenezca al equipo del proyecto y tenga la cuenta activa.
        Set<Long> allowed = project.getTeam().getMembers().stream()
                .map(TeamMember::getUser)
                .filter(User::isEnabled)
                .map(User::getId)
                .collect(Collectors.toSet());

        List<Task> created = new ArrayList<>();
        for (ProposedTask proposed : request.tasks()) {
            Set<User> assignees = proposed.assigneeIdsOrEmpty().stream()
                    .filter(allowed::contains)
                    .map(id -> userRepository.findById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            created.add(taskRepository.save(Task.builder()
                    .title(proposed.title().trim())
                    .description(descripcion(proposed))
                    .type(proposed.type() == null ? TaskType.TAREA : proposed.type())
                    .epic(proposed.epic() == null || proposed.epic().isBlank() ? null : proposed.epic().strip())
                    .priority(proposed.priority() == null ? TaskPriority.MEDIA : proposed.priority())
                    .estimatedHours(proposed.estimatedHours())
                    .dueDate(proposed.dueDate())
                    .boardColumn(column)
                    .assignees(assignees)
                    .createdBy(creator)
                    .aiGenerated(true)
                    .position(position++)
                    .build()));
        }

        activityService.record(project, ActivityAction.GENERACION_IA, "Board", board.getId(),
                "Añadió %d %s generadas con IA a '%s'".formatted(
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
     * Al modelo se le dice que no repita tareas, pero puede que aun asi coincida
     * con algo que ya esta en el tablero: la comprobacion se hace tambien aqui,
     * que es determinista.
     */
    private List<ProposedTask> withoutDuplicates(List<ProposedTask> proposed, List<String> existingTitles) {
        List<String> known = existingTitles.stream().map(TextNormalizer::keywords).toList();

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

    /**
     * El mensaje de usuario: todo lo que cambia de una ejecucion a otra, por
     * apartados, para que el modelo distinga el calendario, el equipo y el
     * documento completo.
     */
    String buildPrompt(String text, PlanTeam team, LocalDate start, int sprintDays,
                       List<AiTaskScheduler.Window> fixedSprints, List<String> existingTitles) {
        StringBuilder prompt = new StringBuilder();
        // Sin esto el modelo planifica en el ano en que se entreno.
        prompt.append("Fecha de hoy: ").append(LocalDate.now()).append('\n');
        prompt.append("Fecha de inicio: ").append(start).append('\n');
        prompt.append("Duracion del sprint: ").append(sprintDays).append(" dias")
              .append(sprintDays % 7 == 0 ? " (" + sprintDays / 7 + (sprintDays == 7 ? " semana)" : " semanas)") : "")
              .append('\n');

        if (fixedSprints != null) {
            prompt.append("Fecha de entrega: ").append(fixedSprints.get(fixedSprints.size() - 1).end()).append('\n');
        }

        prompt.append("\nSPRINTS\n");
        if (fixedSprints != null) {
            prompt.append("Son fijos: usa exactamente estos numeros, uno por cada sprint de la lista.\n");
            for (int i = 0; i < fixedSprints.size(); i++) {
                AiTaskScheduler.Window sprint = fixedSprints.get(i);
                prompt.append("- Sprint ").append(i + 1).append(": ").append(sprint.start())
                      .append(" a ").append(sprint.end()).append('\n');
            }
        } else {
            prompt.append("No hay fecha de entrega fijada: decide cuantos sprints hacen falta ")
                  .append("(entre 1 y ").append(MAX_SPRINTS).append(") y devuelve uno en \"sprints\" por cada uno. ")
                  .append("Empiezan el ").append(start).append(" y cada uno dura ").append(sprintDays)
                  .append(" dias.\n");
        }

        Map<Long, BigDecimal> ritmo = historicalPace(team);
        prompt.append("\nEQUIPO (").append(team.members().size())
              .append(team.members().size() == 1 ? " persona)\n" : " personas)\n");
        for (PlanTeam.Member member : team.members()) {
            prompt.append("- ").append(member.name());
            if (member.leader()) prompt.append(" (lider)");
            BigDecimal pace = ritmo.get(member.userId());
            if (pace != null) {
                prompt.append(" | ritmo historico: termina sus tareas en el ").append(percent(pace))
                      .append(" % de las horas estimadas de media");
            }
            prompt.append(" | rol y preferencias: ")
                  .append(member.preferences().isBlank() ? "sin especificar" : member.preferences().replace('\n', ' '))
                  .append('\n');
        }

        if (!existingTitles.isEmpty()) {
            prompt.append("\nYA ESTA EN EL TABLERO (no lo repitas)\n");
            existingTitles.forEach(title -> prompt.append("- ").append(title).append('\n'));
        }

        prompt.append("\nDOCUMENTO DEL PROYECTO\n").append(text).append('\n');
        return prompt.toString();
    }

    /** Con menos tareas que esto no se cuenta el ritmo: una sola tarea rara no dice nada de nadie. */
    private static final int MIN_TASKS_FOR_PACE = 3;

    /**
     * Ritmo real de cada persona: la razon entre las horas reales y las
     * estimadas en sus tareas ya hechas (con las dos apuntadas), en cualquier
     * proyecto en el que haya trabajado, no solo este. Es una propiedad de la
     * persona, no del proyecto, y sirve para que el modelo calibre las horas que
     * le pone en el proximo reparto en vez de una estimacion generica.
     */
    private Map<Long, BigDecimal> historicalPace(PlanTeam team) {
        Map<Long, BigDecimal> ritmo = new LinkedHashMap<>();
        for (PlanTeam.Member member : team.members()) {
            List<Task> hechas = taskRepository.findWithActualsByAssignee(member.userId());
            if (hechas.size() < MIN_TASKS_FOR_PACE) continue;

            BigDecimal estimadoTotal = hechas.stream().map(Task::getEstimatedHours)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal realTotal = hechas.stream().map(Task::getActualHours)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (estimadoTotal.signum() == 0) continue;

            ritmo.put(member.userId(), realTotal.divide(estimadoTotal, 4, RoundingMode.HALF_UP));
        }
        return ritmo;
    }

    private static int percent(BigDecimal share) {
        return share.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    // ---------------------------------------------------------------------
    // Interpretar la respuesta
    // ---------------------------------------------------------------------

    /** Lo que se pudo leer de una respuesta: las tareas, cuantos sprints hacen falta y el objetivo de cada uno. */
    record ParsedPlan(List<ProposedTask> tasks, Integer numSprints, Map<Integer, SprintGoal> goals,
                      List<String> modelWarnings) {

        record SprintGoal(String objetivo) {
        }
    }

    /** La respuesta no es un plan: se le devuelve al modelo como error a corregir. */
    static class InvalidPlanException extends RuntimeException {
        InvalidPlanException(String message) {
            super(message);
        }
    }

    /**
     * Lee el JSON del modelo. Se aceptan las claves en espanol que pide el prompt y
     * sus equivalentes en ingles, por si el modelo las cambia; lo que no se acepta
     * es algo sin lista de tareas.
     *
     * <p>Las "epicas" y las "dependencias" del prompt van por id (E1, T1...): aqui
     * se resuelven a texto (nombre de la epica, titulo de la tarea previa) antes
     * de construir cada {@link ProposedTask}, que es lo que espera el resto del
     * servicio (la validacion de dependencias ya trabajaba por titulo).</p>
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

        Map<String, String> epicasPorId = epicTitlesById(root);

        // Primera pasada: el id y el titulo de cada tarea, para poder resolver
        // "dependencias" (que van por id) en la segunda.
        Map<String, String> tituloPorId = new LinkedHashMap<>();
        for (JsonNode node : array) {
            String id = text(node, 40, "id");
            String title = text(node, 200, "titulo", "title");
            if (id != null && title != null) tituloPorId.put(id, title);
        }

        List<ProposedTask> tasks = new ArrayList<>();
        for (JsonNode node : array) {
            String title = text(node, 200, "titulo", "title");
            if (title == null) continue;

            List<String> dependsOn = stringList(first(node, "dependencias", "depende_de", "dependsOn")).stream()
                    .map(ref -> tituloPorId.getOrDefault(ref, ref))
                    .toList();
            String epicaRef = text(node, 100, "epica", "epic");

            tasks.add(new ProposedTask(
                    title,
                    text(node, 2000, "descripcion", "description"),
                    text(node, 500, "justificacion", "motivo", "reason"),
                    type(text(node, 40, "tipo", "type")),
                    epicaRef == null ? null : epicasPorId.getOrDefault(epicaRef, epicaRef),
                    integer(first(node, "sprint")),
                    priority(text(node, 20, "prioridad", "priority")),
                    hours(first(node, "horas", "estimatedHours", "hours")),
                    null,
                    List.of(),
                    assigneeNamesOf(node),
                    dependsOn,
                    stringList(first(node, "etiquetas", "labels"))));

            if (tasks.size() >= properties.getMaxTasks()) break;
        }

        Map<Integer, ParsedPlan.SprintGoal> goals = new LinkedHashMap<>();
        JsonNode sprints = root.isArray() ? null : first(root, "sprints");
        if (sprints != null && sprints.isArray()) {
            for (JsonNode sprint : sprints) {
                Integer numero = integer(first(sprint, "nombre", "numero", "name", "number"));
                String objetivo = text(sprint, 300, "objetivo", "goal");
                if (numero != null && objetivo != null) goals.put(numero, new ParsedPlan.SprintGoal(objetivo));
            }
        }
        // Sin fecha de entrega, es el propio modelo quien decide cuantos sprints
        // hacen falta: cuantos ha devuelto en "sprints" es la respuesta.
        Integer numSprints = goals.isEmpty() ? null : goals.size();

        List<String> modelWarnings = root.isArray() ? List.of() : stringList(first(root, "riesgos", "avisos", "warnings"));

        return new ParsedPlan(tasks, numSprints, goals, modelWarnings);
    }

    /** Id -> titulo de cada epica declarada en "epicas", para resolver la referencia que lleva cada tarea. */
    private static Map<String, String> epicTitlesById(JsonNode root) {
        Map<String, String> result = new LinkedHashMap<>();
        JsonNode epicas = root.isArray() ? null : first(root, "epicas", "epics");
        if (epicas == null || !epicas.isArray()) return result;
        for (JsonNode node : epicas) {
            String id = text(node, 40, "id");
            String titulo = text(node, 100, "titulo", "title");
            if (id != null && titulo != null) result.put(id, titulo);
        }
        return result;
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

    /**
     * "Alta", "ALTA" o "alta" son lo mismo. Tambien se acepta la escala de Jira
     * que pide el prompt ("Highest|High|Medium|Low"): "highest" hay que mirarlo
     * antes que "high", porque los dos empiezan igual. Lo que no se entiende, media.
     */
    private static TaskPriority priority(String raw) {
        String valor = TextNormalizer.plain(raw);
        if (valor.startsWith("crit") || valor.startsWith("highest")) return TaskPriority.CRITICA;
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

    /**
     * Los nombres de a quien se reparte una tarea. El prompt pide "responsable"
     * (un solo nombre), pero se acepta cualquier otra forma que el modelo use por
     * su cuenta: un array en plural ("asignados"/"assignees"), un array en
     * singular, o un unico texto en singular.
     */
    private static List<String> assigneeNamesOf(JsonNode node) {
        JsonNode plural = first(node, "asignados", "assignees");
        if (plural != null && plural.isArray()) return stringList(plural);

        JsonNode singular = first(node, "responsable", "asignado", "assignee");
        if (singular != null && singular.isArray()) return stringList(singular);

        String unico = text(node, 150, "responsable", "asignado", "assignee");
        return unico == null ? List.of() : List.of(unico);
    }

    /** Una lista de textos del modelo, sin los vacios. */
    private static List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.isTextual() ? item.asText("").strip() : item.toString();
            if (!value.isBlank()) values.add(value);
        }
        return values;
    }

    private static String abbreviate(String value) {
        if (value == null) return "";
        return value.length() <= 300 ? value : value.substring(0, 300) + "…";
    }
}
