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
import java.math.RoundingMode;
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
 * <p>El documento se manda entero en una sola llamada: GPT-OSS 120B en Groq tiene
 * contexto de sobra (131072 tokens) para el documento completo —el propio
 * {@link DocumentTextExtractor} ya lo capa a 80000 caracteres como red de
 * seguridad, muy por debajo de eso—, asi que no hace falta trocearlo por sprint
 * como con un modelo pequeno en local. El modelo decide el reparto completo,
 * sprint a sprint, de una vez; lo que si es medible —el reparto de horas, las
 * dependencias entre tareas— se comprueba en Java sobre el resultado, no se le
 * pide al modelo que se autoevalue. Las fechas de los sprints y de las tareas las
 * pone siempre el servidor, para que casen con los sprints de la pestana Plan.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlanService {

    /*
     * El reparto por prioridades es lo que hace que respete horas y preferencias:
     * sin decir que manda si chocan, el modelo sacrifica lo que le parece. Las
     * cuentas (capacidad util, margen) ya van hechas en el mensaje de usuario, y
     * el formato solo pide lo que parse() lee: las fechas las pone el servidor, y
     * cada campo de mas es respuesta que no cabe en la cuota de tokens.
     */
    static final String SYSTEM_PROMPT = """
            Eres un Scrum Master y Project Manager senior con más de 15 años convirtiendo enunciados de prácticas universitarias y proyectos de software en backlogs listos para ejecutar. Tu objetivo: un plan realista que cubra todo lo que el documento exige y reparta el trabajo de forma justa según la disponibilidad y las preferencias de cada persona.

            PROCESO (internamente, antes de responder):
            1. Extrae del documento: duración, sprints o iteraciones, cada entregable (con su nombre exacto), los roles que define, requisitos obligatorios, fechas y restricciones. Si una fecha está pendiente de confirmar (ej. "se concretará en Prado"), no la inventes: marca ese sprint como no definitivo.
            2. Lista TODOS los criterios de evaluación, apartados puntuables, requisitos obligatorios y advertencias (penalizaciones, presentaciones, informes, demostraciones). Cada uno necesita al menos una tarea específica y reconocible; no basta una tarea genérica de "implementación" o "pruebas".
            3. Estudia al equipo con los datos del mensaje: capacidad útil de cada persona en cada sprint, su rol y sus preferencias.
            4. Divide el trabajo en tareas concretas de 2 a 8 horas, con la terminología del documento.
            5. Define el objetivo de cada sprint y solo las dependencias que tengan sentido real.
            6. Reparte con las reglas de abajo y revisa antes de responder.

            REGLAS DE REPARTO (por prioridad: si dos chocan, manda la de arriba):
            1. Restricciones: si alguien no está disponible en un periodo o dice que no puede hacer algo, no le asignes nada ahí. Traduce semanas y meses a sprints con las fechas del mensaje. Quien tiene 0 h no recibe tareas.
            2. Roles: si el documento define roles (moderador, gestor de calidad, catalogador...), sus tareas son de quien el documento diga. Si no los define, la gestión (planning, revisión, retrospectiva, entrega) la lleva quien está marcado como (lider), y con 4 o más personas le quitas carga técnica.
            3. Capacidad: lo que suma una persona en un sprint no supera su capacidad útil (ya lleva descontado el margen para imprevistos). Si el trabajo no cabe, no sobrecargues a nadie: pásalo a otro sprint o deja fuera lo menos importante, y dilo en "avisos".
            4. Preferencias: da a cada persona lo que prefiere o domina mientras las reglas anteriores lo permitan. Si tienes que darle algo que pidió evitar, dilo en "avisos".
            5. Equilibrio: carga proporcional a la capacidad, no a partes iguales; quien tiene el doble de horas lleva aproximadamente el doble. Toda persona disponible tiene trabajo en cada sprint: si una fase es de un solo perfil, da tareas de apoyo (documentación, integración, revisión).
            6. Una sola persona por tarea, salvo que la colaboración aporte de verdad (conocimientos complementarios o mucho volumen). Cada responsable dedica las horas completas de la tarea.
            7. Ninguna tarea depende de otra de un sprint posterior.

            CALIDAD:
            - "motivo" cita la parte concreta del documento que justifica la tarea.
            - Tipo: "Story" si aporta funcionalidad o un entregable visible, "Task" si es gestión o soporte.
            - Épica: agrupa por fase o área (entre 3 y 7).
            - Prioridad: "alta" si bloquea a otras, "media" si es importante sin bloquear, "baja" si es mejora.

            AUTORREVISIÓN (antes de responder):
            - ¿Cada criterio de "criterios_detectados" tiene alguna tarea en "tareas_relacionadas"? Si no, añádela.
            - ¿Alguien supera su capacidad útil en algún sprint? ¿Alguien disponible se queda sin trabajo?
            - ¿Se respetan restricciones, roles y preferencias?
            - ¿Las dependencias son lógicas y no van hacia sprints posteriores?
            - ¿Te has inventado algo por falta de información? Dilo en "avisos".
            "avisos" es solo para lo que el equipo tiene que revisar (conflictos, trabajo que no cabe, datos inventados): no confirmes las reglas que sí se cumplen.

            FORMATO DE SALIDA: responde SOLO con un JSON válido, sin texto antes ni después:
            {
              "resumen": {"num_sprints": 0,
                "criterios_detectados": [{"criterio": "", "peso_o_importancia": "", "tareas_relacionadas": [""]}]},
              "sprints": [{"numero": 1, "objetivo": "", "fecha_definitiva": true}],
              "tareas": [{
                "titulo": "", "descripcion": "", "tipo": "Story|Task", "epica": "",
                "sprint": 1, "asignado": [""], "horas": 0, "prioridad": "alta|media|baja",
                "motivo": "", "depende_de": []
              }],
              "avisos": []
            }""";

    /** Sin fecha de entrega, cuantos sprints se aceptan como mucho. */
    private static final int MAX_SPRINTS = 12;

    /**
     * Tope de caracteres del documento que se manda en el prompt, muy por debajo
     * del limite de seguridad de {@link DocumentTextExtractor} (pensado para la
     * ventana de contexto del modelo, de 131072 tokens). Esta cuenta de Groq tiene
     * una cuota de solo 8000 tokens por minuto entre prompt y respuesta: con el
     * prompt de sistema y los datos del equipo ya se van mas de 1500, asi que el
     * documento no puede acercarse a ese limite de contexto sin dejar la respuesta
     * sin sitio donde generarse.
     */
    private static final int DOCUMENT_CHARS_FOR_MODEL = 6_000;

    /**
     * Diferencia en el uso de la capacidad, en tanto por uno, con quien mas usa la
     * suya en un sprint, que ya merece un aviso: 0.35 es un 55 % frente a un 90 %.
     */
    private static final BigDecimal UTILIZATION_GAP_THRESHOLD = new BigDecimal("0.35");

    private final AiProperties properties;
    private final GroqClient groqClient;
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
     * Busca la fecha de inicio en un documento sin generar nada: es deteccion por
     * texto, no una llamada a Groq, asi que se puede pedir antes de generar para
     * no tener que adivinar (y luego regenerar si la adivinanza fallaba).
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> detectStartDate(MultipartFile file) {
        return DocumentDateDetector.detect(documentTextExtractor.extract(file));
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

    private AiPlanResponse plan(Project project, String text, String source, PlanOptions requested) {
        if (!properties.isEnabled()) {
            throw new AiUnavailableException("El modulo de IA esta desactivado en la configuracion del servidor");
        }
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

        List<Task> existing = existingTasks(project);
        // Se busca en el documento entero, antes de recortarlo: la evaluacion suele
        // ir al final, justo la parte que un recorte por longitud se dejaria fuera.
        Optional<String> criteriaExcerpt = EvaluationSectionDetector.detect(text);
        String userPrompt = buildPrompt(truncateForModel(text), criteriaExcerpt, team, start, sprintDays,
                fixedWindows, existing);
        ParsedPlanResult result = runPlan(userPrompt);

        List<AiTaskScheduler.Window> ventanas = fixedWindows != null ? fixedWindows
                : aiTaskScheduler.consecutiveSprints(start, sprintCountFrom(result.parsed()), sprintDays);

        List<String> deliveryWarnings = new ArrayList<>(IterationDeliveryDetector.detect(text).stream()
                .map(IterationDeliveryDetector.Delivery::describe).toList());
        if (text.length() > DOCUMENT_CHARS_FOR_MODEL) {
            deliveryWarnings.add(("El documento tiene %,d caracteres: al modelo solo se le han mandado los "
                    + "primeros %,d para no superar la cuota de tokens por minuto de Groq. Puede faltar contenido "
                    + "de las ultimas paginas en el plan.").formatted(text.length(), DOCUMENT_CHARS_FOR_MODEL));
        }

        return toResponse(project, source, team, start, ventanas, result, deliveryWarnings);
    }

    /** Solo para lo que entra en el prompt: el resto de usos del documento (fechas) necesitan el texto entero. */
    private static String truncateForModel(String text) {
        return text.length() <= DOCUMENT_CHARS_FOR_MODEL ? text
                : text.substring(0, DOCUMENT_CHARS_FOR_MODEL) + "\n\n[...documento recortado por longitud...]";
    }

    /** Lo que ha salido de la llamada al modelo: el plan interpretado y cuantos intentos ha costado. */
    private record ParsedPlanResult(ParsedPlan parsed, int attempts) {
    }

    /**
     * Llama al modelo con el documento completo. Si el JSON no parsea, reintenta
     * hasta el limite configurado; si Groq limita por cuota de tokens (429), espera
     * lo que la propia API dice y lo intenta otra vez, en vez de fallar a la
     * primera por algo que se resuelve solo con tiempo.
     */
    private ParsedPlanResult runPlan(String userPrompt) {
        int intentos = 0;
        for (int intento = 0; intento <= properties.getMaxRetries(); intento++) {
            intentos++;
            String raw;
            try {
                raw = groqClient.chatJson(SYSTEM_PROMPT, userPrompt);
            } catch (GroqClient.RateLimitedException ex) {
                if (intento == properties.getMaxRetries()) throw ex;
                log.warn("IA: Groq ha limitado la peticion en el intento {}, esperando {} s antes de reintentar",
                        intentos, ex.getRetryAfterSeconds());
                esperar(ex.getRetryAfterSeconds());
                continue;
            }
            try {
                return new ParsedPlanResult(parse(raw), intentos);
            } catch (InvalidPlanException ex) {
                log.warn("IA: respuesta no interpretable en el intento {}: {}", intentos, abbreviate(raw));
            }
        }
        throw new AiUnavailableException(
                "El modelo no ha devuelto un plan que se pueda leer tras %d intentos. Vuelve a intentarlo."
                        .formatted(intentos));
    }

    private static void esperar(double segundos) {
        try {
            Thread.sleep((long) Math.ceil(segundos * 1000));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Interrumpido mientras se esperaba para reintentar", ex);
        }
    }

    /** Sin fecha de entrega: los que diga el modelo, dentro de lo razonable. */
    private int sprintCountFrom(ParsedPlan parsed) {
        int propuestos = parsed.numSprints() != null ? parsed.numSprints()
                : parsed.tasks().stream().map(ProposedTask::sprint).filter(Objects::nonNull)
                        .max(Integer::compare).orElse(1);
        return Math.max(1, Math.min(MAX_SPRINTS, propuestos));
    }

    /** Junta lo que ha devuelto el modelo, resuelve fechas y personas, y calcula los avisos. */
    private AiPlanResponse toResponse(Project project, String source, PlanTeam team, LocalDate start,
                                      List<AiTaskScheduler.Window> ventanas, ParsedPlanResult result,
                                      List<String> deliveryWarnings) {
        ParsedPlan parsed = result.parsed();
        List<Task> existing = existingTasks(project);

        // Un sprint que no exista se trata como "sin sprint", no se descarta la tarea.
        List<ProposedTask> conSprintValido = parsed.tasks().stream()
                .map(task -> sprintValido(task.sprint(), ventanas.size()) ? task : withSprint(task, null))
                .toList();

        List<ProposedTask> sinDuplicados = withoutDuplicates(conSprintValido, existing);
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
                    ids, nombres, task.dependsOnOrEmpty());
        }).toList();

        DependencyCheck depCheck = validateDependencies(tasks);
        tasks = depCheck.tasks();

        List<String> warnings = new ArrayList<>();
        warnings.addAll(capacityWarnings(tasks, team, ventanas, parsed.modelWarnings()));
        warnings.addAll(pendingWarnings(tasks));
        warnings.addAll(depCheck.warnings());
        warnings.addAll(sprintGoalWarnings(parsed.goals(), ventanas.size()));
        warnings.addAll(criteriaWarnings(parsed.criteria()));
        warnings.addAll(parsed.modelWarnings());
        warnings.addAll(deliveryWarnings);

        List<PlannedSprint> sprints = new ArrayList<>();
        for (int i = 0; i < ventanas.size(); i++) {
            ParsedPlan.SprintGoal goal = parsed.goals().get(i + 1);
            sprints.add(new PlannedSprint(i + 1, ventanas.get(i).start(), ventanas.get(i).end(),
                    goal == null ? null : goal.objetivo()));
        }

        LocalDate fin = ventanas.get(ventanas.size() - 1).end();
        long dias = new AiTaskScheduler.Window(start, fin).days();
        int semanas = (int) Math.ceil(dias / 7.0);

        log.info("IA: {} tareas en {} sprints para el proyecto {} ({} intentos, {} avisos)",
                tasks.size(), sprints.size(), project.getId(), result.attempts(), warnings.size());

        return AiPlanResponse.of(project.getId(), properties.getModel(), source, semanas, sprints, tasks,
                workload(tasks, team, ventanas), warnings, duplicates, result.attempts());
    }

    private static boolean sprintValido(Integer sprint, int totalSprints) {
        return sprint != null && sprint >= 1 && sprint <= totalSprints;
    }

    private static ProposedTask withSprint(ProposedTask task, Integer sprint) {
        return new ProposedTask(task.title(), task.description(), task.reason(), task.type(), task.epic(),
                sprint, task.priority(), task.estimatedHours(), task.dueDate(), task.assigneeIds(),
                task.assigneeNames(), task.dependsOnOrEmpty());
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
                    warnings.add("Se ha descartado una dependencia de «%s» hacia «%s»: no es valida (no existe, es ella misma o esta en un sprint posterior)."
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
                task.assigneeNames(), dependsOn);
    }

    /**
     * Lo medible del reparto, contra las horas de cada persona y no en absoluto:
     * quien tiene la mitad de horas debe llevar la mitad, y eso no es un
     * desequilibrio. Se avisa si alguien pasa de su capacidad en un sprint, si
     * usa mucho menos de la suya que quien mas usa, o si alguien sin horas tiene
     * tareas. No se corrige inventando o quitando tareas: eso lo decidio el reparto
     * del modelo.
     *
     * <p>Quien se queda sin nada en un sprint porque sus preferencias dicen que no
     * esta (vacaciones, examenes) no es un desequilibrio, pero aqui no se puede
     * leer eso del texto libre. Si el propio modelo lo ha explicado en sus avisos,
     * nombrando a esa persona, no se repite con un aviso que lo contradice.</p>
     */
    private List<String> capacityWarnings(List<ProposedTask> tasks, PlanTeam team,
                                          List<AiTaskScheduler.Window> ventanas, List<String> modelWarnings) {
        String explicado = TextNormalizer.plain(String.join(" ", modelWarnings));
        List<String> warnings = new ArrayList<>();
        List<PlanTeam.Member> disponibles = team.members().stream().filter(PlanTeam.Member::available).toList();

        for (int sprint = 1; sprint <= ventanas.size(); sprint++) {
            Map<Long, BigDecimal> horas = hoursBySprint(tasks, sprint);
            long dias = ventanas.get(sprint - 1).days();

            Map<PlanTeam.Member, BigDecimal> uso = new LinkedHashMap<>();
            for (PlanTeam.Member member : disponibles) {
                BigDecimal suyas = horas.getOrDefault(member.userId(), BigDecimal.ZERO);
                BigDecimal capacidad = member.capacityFor(dias);
                if (capacidad.signum() == 0) continue;
                if (suyas.compareTo(capacidad) > 0) {
                    warnings.add("En el sprint %d, %s tiene %s h y solo dispone de %s h: revisa el reparto."
                            .formatted(sprint, member.name(), plano(suyas), plano(capacidad)));
                }
                uso.put(member, suyas.divide(capacidad, 4, RoundingMode.HALF_UP));
            }

            Map.Entry<PlanTeam.Member, BigDecimal> maximo = uso.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).orElse(null);
            if (uso.size() < 2 || maximo == null || maximo.getValue().signum() == 0) continue;
            for (Map.Entry<PlanTeam.Member, BigDecimal> entry : uso.entrySet()) {
                boolean parado = entry.getValue().signum() == 0;
                if (parado && explicado.contains(TextNormalizer.plain(entry.getKey().name()))) continue;
                if (maximo.getValue().subtract(entry.getValue()).compareTo(UTILIZATION_GAP_THRESHOLD) > 0) {
                    warnings.add(("En el sprint %d, %s usa el %d%% de sus horas frente al %d%% de %s: "
                            + "revisa el reparto.").formatted(sprint, entry.getKey().name(),
                            percent(entry.getValue()), percent(maximo.getValue()), maximo.getKey().name()));
                }
            }
        }

        for (PlanTeam.Member member : team.members()) {
            if (member.available()) continue;
            long suyas = tasks.stream().filter(task -> task.assigneeIdsOrEmpty().contains(member.userId())).count();
            if (suyas > 0) {
                warnings.add("%s no tiene horas disponibles y se le %s: revisa el reparto.".formatted(member.name(),
                        suyas == 1 ? "ha asignado 1 tarea" : "han asignado " + suyas + " tareas"));
            }
        }
        return warnings;
    }

    /**
     * Horas de cada persona en un sprint. Cada responsable de una tarea se lleva
     * sus horas completas, no repartidas: cada uno invierte ese tiempo.
     */
    private static Map<Long, BigDecimal> hoursBySprint(List<ProposedTask> tasks, int sprint) {
        Map<Long, BigDecimal> horas = new HashMap<>();
        for (ProposedTask task : tasks) {
            if (!Objects.equals(task.sprint(), sprint) || task.estimatedHours() == null) continue;
            for (Long id : task.assigneeIdsOrEmpty()) {
                horas.merge(id, task.estimatedHours(), BigDecimal::add);
            }
        }
        return horas;
    }

    private static int percent(BigDecimal share) {
        return share.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /** Las tareas que el modelo ha dejado marcadas como incompletas, para que el usuario las revise. */
    private List<String> pendingWarnings(List<ProposedTask> tasks) {
        return tasks.stream()
                .filter(task -> task.reason() != null && task.reason().toUpperCase(Locale.ROOT).startsWith("PENDIENTE:"))
                .map(task -> "«%s»: %s".formatted(task.title(), task.reason()))
                .toList();
    }

    /** Un sprint sin fecha exacta confirmada segun el propio documento, tal y como lo ha marcado el modelo. */
    private List<String> sprintGoalWarnings(Map<Integer, ParsedPlan.SprintGoal> goals, int numSprints) {
        List<String> warnings = new ArrayList<>();
        for (int numero = 1; numero <= numSprints; numero++) {
            ParsedPlan.SprintGoal goal = goals.get(numero);
            if (goal != null && !goal.definitiva()) {
                warnings.add("El sprint %d no tiene fecha exacta confirmada segun el documento.".formatted(numero));
            }
        }
        return warnings;
    }

    /**
     * El propio modelo dice que criterios de evaluacion o requisitos ha detectado
     * y que tareas los cubren ({@code criterios_detectados}, ver el prompt). Si
     * alguno se queda sin ninguna tarea asociada, se avisa en vez de dejarlo
     * pasar en silencio: no se corrige inventando una tarea, porque eso ya le
     * tocaba decidirlo al reparto del modelo.
     */
    private List<String> criteriaWarnings(List<ParsedPlan.Criterion> criteria) {
        return criteria.stream()
                .filter(ParsedPlan.Criterion::sinCobertura)
                .map(criterion -> "El criterio '%s' no tiene ninguna tarea asociada; revisalo manualmente."
                        .formatted(criterion.texto()))
                .toList();
    }

    /** Horas y tareas de cada persona, contadas sobre el plan: no sobre lo que dice el modelo de si mismo. */
    private List<MemberWorkload> workload(List<ProposedTask> tasks, PlanTeam team,
                                          List<AiTaskScheduler.Window> ventanas) {
        return team.members().stream().map(member -> {
            List<ProposedTask> suyas = tasks.stream()
                    .filter(task -> task.assigneeIdsOrEmpty().contains(member.userId()))
                    .toList();
            BigDecimal horas = suyas.stream()
                    .map(task -> task.estimatedHours() == null ? BigDecimal.ZERO : task.estimatedHours())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal capacidad = ventanas.stream()
                    .map(ventana -> member.capacityFor(ventana.days()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new MemberWorkload(member.userId(), member.name(), suyas.size(), horas, capacidad);
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

        // Solo se asigna a gente que pertenezca al equipo del proyecto.
        Set<Long> allowed = project.getTeam().getMembers().stream()
                .map(member -> member.getUser().getId())
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
     * Al modelo se le dice que no repita tareas, pero puede que aun asi coincida
     * con algo que ya esta en el tablero: la comprobacion se hace tambien aqui,
     * que es determinista.
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

    /**
     * El mensaje de usuario: todo lo que cambia de una ejecucion a otra, por
     * apartados, para que el modelo distinga el calendario, el equipo y el
     * documento completo.
     */
    String buildPrompt(String text, Optional<String> criteriaExcerpt, PlanTeam team, LocalDate start, int sprintDays,
                       List<AiTaskScheduler.Window> fixedSprints, List<Task> existing) {
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
            prompt.append("Son fijos: usa exactamente estos numeros, uno por cada sprint de la lista. ")
                  .append("La capacidad util es el maximo de horas de cada persona en ese sprint.\n");
            for (int i = 0; i < fixedSprints.size(); i++) {
                AiTaskScheduler.Window sprint = fixedSprints.get(i);
                BigDecimal total = team.members().stream()
                        .map(member -> member.usefulCapacityFor(sprint.days()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                prompt.append("- Sprint ").append(i + 1).append(": ").append(sprint.start())
                      .append(" a ").append(sprint.end()).append(" · capacidad util: ")
                      .append(team.members().stream()
                              .map(member -> member.name() + " " + plano(member.usefulCapacityFor(sprint.days())) + " h")
                              .collect(Collectors.joining(", ")))
                      .append(" (equipo ").append(plano(total)).append(" h)\n");
            }
        } else {
            prompt.append("No hay fecha de entrega fijada: decide cuantos sprints hacen falta ")
                  .append("(entre 1 y ").append(MAX_SPRINTS).append(") y ponlo en \"resumen.num_sprints\". ")
                  .append("Empiezan el ").append(start).append(" y cada uno dura ").append(sprintDays)
                  .append(" dias.\n");
        }

        prompt.append("\nEQUIPO (").append(team.members().size())
              .append(team.members().size() == 1 ? " persona)\n" : " personas)\n");
        for (PlanTeam.Member member : team.members()) {
            prompt.append("- ").append(member.name());
            if (member.leader()) prompt.append(" (lider)");
            prompt.append(" | ").append(member.hoursPerWeek()).append(" h/semana");
            if (!member.available()) {
                prompt.append(" (no disponible)");
            } else if (fixedSprints == null) {
                prompt.append(" | capacidad util: ").append(plano(member.usefulCapacityFor(sprintDays)))
                      .append(" h por sprint");
            }
            prompt.append(" | rol y preferencias: ")
                  .append(member.preferences().isBlank() ? "sin especificar" : member.preferences().replace('\n', ' '))
                  .append('\n');
        }

        if (!existing.isEmpty()) {
            prompt.append("\nYA ESTA EN EL TABLERO (no lo repitas)\n");
            existing.forEach(task -> prompt.append("- ").append(task.getTitle()).append('\n'));
        }

        // Va aparte y siempre entero, aunque el documento de mas abajo este recortado
        // por longitud: si esto solo apareciera en la parte recortada, el reparto
        // ignoraria por completo lo que puntua o es obligatorio.
        criteriaExcerpt.ifPresent(excerpt -> prompt.append("\nCRITERIOS DE EVALUACION Y REQUISITOS DETECTADOS EN EL ")
                .append("DOCUMENTO\n(puede que este fragmento no aparezca completo en el documento de mas abajo, ")
                .append("si es largo; no lo ignores por eso)\n").append(excerpt).append('\n'));

        prompt.append("\nDOCUMENTO DEL PROYECTO\n").append(text).append('\n');
        return prompt.toString();
    }

    private static String plano(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    // ---------------------------------------------------------------------
    // Interpretar la respuesta
    // ---------------------------------------------------------------------

    /** Lo que se pudo leer de una respuesta: las tareas, cuantos sprints hacen falta y el objetivo de cada uno. */
    record ParsedPlan(List<ProposedTask> tasks, Integer numSprints, Map<Integer, SprintGoal> goals,
                      List<String> modelWarnings, List<Criterion> criteria) {

        /** El objetivo de un sprint, y si su fecha es definitiva o esta pendiente de confirmar. */
        record SprintGoal(String objetivo, boolean definitiva) {
        }

        /**
         * Un criterio de evaluacion, apartado puntuable o requisito obligatorio que
         * el propio modelo dice haber detectado en el documento, y que titulos de
         * tarea dice que lo cubren. Sirve para comprobar en Java que no se queda
         * ninguno sin tarea, sin depender de que el modelo se autoevalue bien.
         */
        record Criterion(String texto, String importancia, List<String> tareasRelacionadas) {

            boolean sinCobertura() {
                return tareasRelacionadas == null || tareasRelacionadas.isEmpty();
            }
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
                    List.of(),
                    assigneeNamesOf(node),
                    stringList(first(node, "depende_de", "dependsOn"))));

            if (tasks.size() >= properties.getMaxTasks()) break;
        }

        JsonNode resumen = root.isArray() ? null : first(root, "resumen", "summary");
        Integer numSprints = resumen == null ? null : integer(first(resumen, "num_sprints", "numSprints"));

        Map<Integer, ParsedPlan.SprintGoal> goals = new HashMap<>();
        JsonNode sprints = root.isArray() ? null : first(root, "sprints");
        if (sprints != null && sprints.isArray()) {
            for (JsonNode sprint : sprints) {
                Integer numero = integer(first(sprint, "numero", "number"));
                String objetivo = text(sprint, 300, "objetivo", "goal");
                boolean definitiva = bool(first(sprint, "fecha_definitiva", "definitive"), true);
                if (numero != null && objetivo != null) goals.put(numero, new ParsedPlan.SprintGoal(objetivo, definitiva));
            }
        }

        List<String> modelWarnings = root.isArray() ? List.of() : stringList(first(root, "avisos", "warnings"));

        List<ParsedPlan.Criterion> criteria = new ArrayList<>();
        JsonNode criteriosNode = resumen == null ? null : first(resumen, "criterios_detectados", "detectedCriteria");
        if (criteriosNode != null && criteriosNode.isArray()) {
            for (JsonNode node : criteriosNode) {
                String criterio = text(node, 500, "criterio", "criterion");
                if (criterio == null) continue;
                criteria.add(new ParsedPlan.Criterion(criterio, text(node, 100, "peso_o_importancia", "weight"),
                        stringList(first(node, "tareas_relacionadas", "relatedTasks"))));
            }
        }

        return new ParsedPlan(tasks, numSprints, goals, modelWarnings, criteria);
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

    /** Un booleano, venga como booleano o como texto ("si"/"no"). Si no se entiende, el valor por defecto. */
    private static boolean bool(JsonNode value, boolean defecto) {
        if (value == null || value.isNull() || value.isMissingNode()) return defecto;
        if (value.isBoolean()) return value.asBoolean();
        String texto = value.asText("").strip().toLowerCase(Locale.ROOT);
        if (texto.equals("false") || texto.equals("no")) return false;
        if (texto.equals("true") || texto.equals("si") || texto.equals("sí")) return true;
        return defecto;
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

    /**
     * Los nombres de a quien se reparte una tarea. Se acepta, por robustez ante
     * como el modelo decida darle forma: un array bajo la clave en plural
     * ("asignados"/"assignees"), un array bajo la clave en singular (si el modelo
     * mezcla formato), o un unico texto bajo la clave en singular (compatibilidad
     * con el formato de antes de admitir varios responsables).
     */
    private static List<String> assigneeNamesOf(JsonNode node) {
        JsonNode plural = first(node, "asignados", "assignees");
        if (plural != null && plural.isArray()) return stringList(plural);

        JsonNode singular = first(node, "asignado", "assignee");
        if (singular != null && singular.isArray()) return stringList(singular);

        String unico = text(node, 150, "asignado", "assignee");
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
