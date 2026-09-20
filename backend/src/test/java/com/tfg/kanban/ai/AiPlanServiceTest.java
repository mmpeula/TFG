package com.tfg.kanban.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfg.kanban.TestSecurity;
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
import com.tfg.kanban.task.TaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El punto fragil del modulo de IA no es la llamada HTTP sino interpretar lo que
 * devuelve el modelo: aqui se prueban las formas de respuesta que se han visto en
 * la practica, incluidas las que no siguen el formato pedido.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiPlanServiceTest {

    @Mock private OllamaClient ollamaClient;
    @Mock private ProjectService projectService;
    @Mock private BoardService boardService;
    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private ActivityService activityService;
    @Mock private TaskMapper taskMapper;

    private AiProperties properties;
    private AiPlanService service;
    private Project project;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAs(1L, "RESPONSABLE");

        properties = new AiProperties();
        properties.setModel("llama3");
        properties.setMaxTasks(8);

        service = new AiPlanService(properties, ollamaClient, new ObjectMapper(), projectService,
                boardService, taskRepository, userRepository, activityService, taskMapper,
                new DocumentTextExtractor(), new WorkloadBalancer(), new AiTaskScheduler());

        Team team = Team.builder().id(1L).name("Grupo 4").build();
        team.getMembers().add(TeamMember.builder().id(1L)
                .team(team).user(User.builder().id(1L).fullName("Ana Garcia").build()).build());
        team.getMembers().add(TeamMember.builder().id(2L)
                .team(team).user(User.builder().id(2L).fullName("Luis Perez").build()).build());
        // El profesor: DOCENTE en el equipo, no un alumno. No debe entrar en el reparto.
        team.getMembers().add(TeamMember.builder().id(3L).role(Role.DOCENTE)
                .team(team).user(User.builder().id(3L).fullName("Profesor Soto").build()).build());
        project = Project.builder()
                .id(7L).name("Practica 2").team(team)
                .briefing("Implementar una API REST de biblioteca con JWT.")
                .deadline(LocalDate.now().plusDays(30))
                .build();
        when(projectService.getProject(7L)).thenReturn(project);
        when(taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    private void modelReplies(String json) {
        when(ollamaClient.chatJson(anyString(), anyString())).thenReturn(json);
    }

    @Test
    @DisplayName("Interpreta la respuesta con el formato pedido")
    void parsesExpectedShape() {
        modelReplies("""
                {"tasks":[
                  {"title":"Modelar el esquema","reason":"El guion pide un modelo E/R","tag":"analisis","priority":"ALTA","estimatedHours":6},
                  {"title":"Implementar el login","reason":"Hace falta JWT","tag":"backend","priority":"MEDIA","estimatedHours":4}
                ]}""");

        var response = service.generate(7L, null, null);

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.model()).isEqualTo("llama3");
        assertThat(response.tasks()).hasSize(2);
        assertThat(response.tasks().get(0).title()).isEqualTo("Modelar el esquema");
        assertThat(response.tasks().get(0).priority()).isEqualTo(TaskPriority.ALTA);
        assertThat(response.tasks().get(0).estimatedHours()).isEqualByComparingTo("6");
        assertThat(response.message()).contains("2 tareas");
        // El documento de este test no dice nada de fechas: no hay que inventarlas.
        assertThat(response.schedule()).isNull();
    }

    @Test
    @DisplayName("Las indicaciones del usuario llegan al modelo, aparte del guion")
    void indicationsReachThePrompt() {
        modelReplies("""
                {"tasks":[{"title":"Modelar el esquema"}]}""");

        service.generate(7L, null, "Que Ana lleve menos carga esta semana, tiene examenes");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollamaClient).chatJson(anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("Indicaciones del usuario")
                .contains("Que Ana lleve menos carga esta semana, tiene examenes");
    }

    @Test
    @DisplayName("Sin indicaciones, el prompt no menciona el apartado")
    void noIndicationsMeansNoExtraSection() {
        modelReplies("""
                {"tasks":[{"title":"Modelar el esquema"}]}""");

        service.generate(7L, null, "   ");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollamaClient).chatJson(anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).doesNotContain("Indicaciones del usuario");
    }

    @Test
    @DisplayName("Una indicacion desmedida se recorta antes de mandarla al modelo")
    void indicationsAreTruncated() {
        modelReplies("""
                {"tasks":[{"title":"Modelar el esquema"}]}""");
        String larga = "a".repeat(600);

        service.generate(7L, null, larga);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollamaClient).chatJson(anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).doesNotContain("a".repeat(501));
    }

    // ------------------------------------------------- excluir del reparto

    @Test
    @DisplayName("A quien las indicaciones piden excluir no le toca ninguna tarea, aunque el reparto normalmente no deje a nadie a cero")
    void excludesTheNamedMemberFromTheReparto() {
        modelReplies("""
                {"tasks":[{"title":"Tarea A"},{"title":"Tarea B"},{"title":"Tarea C"}],
                 "excluded":["Luis Perez"]}""");

        var response = service.generate(7L, null, "Luis esta de baja, que no le toque nada esta vez");

        assertThat(response.excludedMembers()).containsExactly("Luis Perez");
        assertThat(response.tasks()).extracting(ProposedTask::assigneeId).doesNotContain(2L);
        assertThat(response.workload()).extracting(w -> w.memberId()).doesNotContain(2L);
    }

    @Test
    @DisplayName("Sin indicaciones de exclusion, nadie se queda fuera")
    void noExclusionByDefault() {
        modelReplies("""
                {"tasks":[{"title":"Tarea A"}]}""");

        assertThat(service.generate(7L, null, null).excludedMembers()).isEmpty();
    }

    @Test
    @DisplayName("Un nombre excluido que no corresponde a nadie del equipo se ignora, no se inventa a quien excluir")
    void unmatchedExclusionNameIsIgnored() {
        modelReplies("""
                {"tasks":[{"title":"Tarea A"}], "excluded":["Pedro El Inexistente"]}""");

        var response = service.generate(7L, null, null);

        assertThat(response.excludedMembers()).isEmpty();
        assertThat(response.tasks()).singleElement().satisfies(t -> assertThat(t.assigneeId()).isNotNull());
    }

    @Test
    @DisplayName("Si se excluye a todo el equipo, las tareas vuelven sin asignar en vez de fallar")
    void excludingEveryoneLeavesTasksUnassigned() {
        modelReplies("""
                {"tasks":[{"title":"Tarea A"}], "excluded":["Ana Garcia","Luis Perez"]}""");

        var response = service.generate(7L, null, null);

        assertThat(response.excludedMembers()).containsExactlyInAnyOrder("Ana Garcia", "Luis Perez");
        assertThat(response.workload()).isEmpty();
        assertThat(response.tasks()).singleElement().satisfies(t -> assertThat(t.assigneeId()).isNull());
    }

    // ------------------------------------------------- el profesor no es un alumno

    @Test
    @DisplayName("El profesor (DOCENTE) nunca recibe tareas del reparto")
    void observerNeverGetsAssignedWork() {
        modelReplies("""
                {"tasks":[{"title":"A"},{"title":"B"},{"title":"C"},{"title":"D"},{"title":"E"}]}""");

        var response = service.generate(7L, null, null);

        assertThat(response.tasks()).extracting(ProposedTask::assigneeId).doesNotContain(3L);
        assertThat(response.workload()).extracting(w -> w.memberId()).doesNotContain(3L);
    }

    @Test
    @DisplayName("El profesor no cuenta en la lista de personas que ve el modelo para repartir")
    void observerIsNotShownToTheModelAsAssignable() {
        modelReplies("""
                {"tasks":[{"title":"A"}]}""");

        service.generate(7L, null, null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollamaClient).chatJson(anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("Ana Garcia").contains("Luis Perez")
                .doesNotContain("Profesor Soto");
    }

    @Test
    @DisplayName("Si el modelo igualmente sugiere al profesor, la sugerencia se ignora")
    void suggestingTheObserverIsIgnored() {
        modelReplies("""
                {"tasks":[{"title":"A","assignee":"Profesor Soto"}]}""");

        var response = service.generate(7L, null, null);

        assertThat(response.tasks()).singleElement()
                .satisfies(t -> assertThat(t.assigneeId()).isNotEqualTo(3L));
    }

    /*
     * Las fechas de estas pruebas son relativas a hoy a proposito: la planificacion
     * propuesta se valida contra la fecha actual, asi que una fecha fija acabaria
     * quedandose en el pasado y rompiendo la prueba con el tiempo.
     */
    private static final LocalDate INICIO = LocalDate.now().plusWeeks(2);
    private static final LocalDate ENTREGA = LocalDate.now().plusWeeks(12);

    @Test
    @DisplayName("Si el documento dice fechas y duracion de sprint, se propone al usuario")
    void parsesScheduleWhenDocumentStatesIt() {
        modelReplies("""
                {"tasks":[{"title":"Modelar el esquema"}],
                 "schedule":{"startDate":"%s","deadline":"%s","sprintDays":14}}""".formatted(INICIO, ENTREGA));

        var schedule = service.generate(7L, null, null).schedule();

        assertThat(schedule).isNotNull();
        assertThat(schedule.startDate()).isEqualTo(INICIO);
        assertThat(schedule.deadline()).isEqualTo(ENTREGA);
        assertThat(schedule.sprintDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("Las claves en espanol tambien valen para las fechas del documento")
    void parsesSpanishAliasesForSchedule() {
        modelReplies("""
                {"tasks":[{"title":"Modelar el esquema"}],
                 "schedule":{"fechaInicio":"%s","fechaEntrega":"%s","duracionSprint":7}}"""
                .formatted(INICIO, ENTREGA));

        var schedule = service.generate(7L, null, null).schedule();

        assertThat(schedule).isNotNull();
        assertThat(schedule.startDate()).isEqualTo(INICIO);
        assertThat(schedule.sprintDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("Un curso ya pasado se adelanta al calendario actual: el dia y el mes son del enunciado, el ano lo inventa el modelo")
    void movesAStaleScheduleIntoTheCurrentCalendar() {
        LocalDate inicioViejo = LocalDate.now().minusYears(1).plusWeeks(3);
        LocalDate entregaVieja = inicioViejo.plusWeeks(9);
        modelReplies("""
                {"tasks":[{"title":"Modelar el esquema"}],
                 "schedule":{"startDate":"%s","deadline":"%s","sprintDays":21}}"""
                .formatted(inicioViejo, entregaVieja));

        var schedule = service.generate(7L, null, null).schedule();

        assertThat(schedule).isNotNull();
        assertThat(schedule.sprintDays()).isEqualTo(21);
        // Mismo dia y mes, un ano despues: la ventana se conserva entera.
        assertThat(schedule.startDate()).isEqualTo(inicioViejo.plusYears(1));
        assertThat(schedule.deadline()).isEqualTo(entregaVieja.plusYears(1));
        assertThat(schedule.deadline()).isAfter(LocalDate.now());
    }

    @Test
    @DisplayName("Una fecha de inicio suelta y pasada tambien se adelanta")
    void movesAStalePastStartDateWithoutDeadline() {
        LocalDate inicioViejo = LocalDate.now().minusMonths(2);
        modelReplies("""
                {"tasks":[{"title":"Modelar el esquema"}], "schedule":{"startDate":"%s"}}"""
                .formatted(inicioViejo));

        var schedule = service.generate(7L, null, null).schedule();

        assertThat(schedule).isNotNull();
        assertThat(schedule.startDate()).isEqualTo(inicioViejo.plusYears(1));
    }

    @Test
    @DisplayName("Una practica ya empezada no se toca: lo que manda es que la entrega siga por venir")
    void keepsAPastStartWhenTheDeadlineIsStillAhead() {
        LocalDate empezoHaceUnMes = LocalDate.now().minusMonths(1);
        modelReplies("""
                {"tasks":[{"title":"Modelar el esquema"}],
                 "schedule":{"startDate":"%s","deadline":"%s"}}"""
                .formatted(empezoHaceUnMes, ENTREGA));

        var schedule = service.generate(7L, null, null).schedule();

        assertThat(schedule).isNotNull();
        assertThat(schedule.startDate()).isEqualTo(empezoHaceUnMes);
        assertThat(schedule.deadline()).isEqualTo(ENTREGA);
    }

    @Test
    @DisplayName("Una entrega anterior al inicio no describe ninguna ventana: se queda solo la entrega")
    void dropsStartDateWhenItIsAfterTheDeadline() {
        modelReplies("""
                {"tasks":[{"title":"Modelar el esquema"}],
                 "schedule":{"startDate":"%s","deadline":"%s"}}"""
                .formatted(LocalDate.now().plusMonths(6), ENTREGA));

        var schedule = service.generate(7L, null, null).schedule();

        assertThat(schedule).isNotNull();
        assertThat(schedule.startDate()).isNull();
        assertThat(schedule.deadline()).isEqualTo(ENTREGA);
    }

    @Test
    @DisplayName("Las fechas que se proponen son las mismas con las que se fechan las tareas")
    void proposedScheduleAndTaskDatesAgree() {
        modelReplies("""
                {"tasks":[{"title":"A"},{"title":"B"},{"title":"C"}],
                 "schedule":{"startDate":"%s","deadline":"%s","sprintDays":14}}"""
                .formatted(INICIO, ENTREGA));

        var response = service.generate(7L, null, null);

        // Si divergieran, el usuario aplicaria al workspace una ventana en la que no
        // cae ninguna de sus tareas y el plan saldria con sprints vacios de sobra.
        assertThat(response.tasks()).allSatisfy(task -> assertThat(task.dueDate())
                .isBetween(response.schedule().startDate(), response.schedule().deadline()));
    }

    @Test
    @DisplayName("Una fecha que no se entiende, o una duracion de sprint absurda, se ignora en vez de adivinar")
    void ignoresUnusableScheduleValues() {
        modelReplies("""
                {"tasks":[{"title":"Modelar el esquema"}],
                 "schedule":{"startDate":"proximamente","deadline":"","sprintDays":500}}""");

        assertThat(service.generate(7L, null, null).schedule()).isNull();
    }

    @Test
    @DisplayName("Un dato suelto de fecha no se descarta solo porque falten los demas")
    void keepsPartialSchedule() {
        modelReplies("""
                {"tasks":[{"title":"Modelar el esquema"}], "schedule":{"sprintDays":21}}""");

        var schedule = service.generate(7L, null, null).schedule();

        assertThat(schedule).isNotNull();
        assertThat(schedule.sprintDays()).isEqualTo(21);
        assertThat(schedule.startDate()).isNull();
        assertThat(schedule.deadline()).isNull();
    }

    @Test
    @DisplayName("Las fechas de las tareas caen dentro de la ventana que el documento acaba de decir, no en una inventada aparte")
    void taskDatesMatchTheJustDetectedSchedule() {
        // Lejos tanto de "hoy + 42 dias" (la ventana por defecto) como del deadline
        // que ya tuviera guardado el proyecto (hoy + 30 dias, puesto en el setUp):
        // si las tareas caen aqui es porque se ha usado la fecha del documento, no
        // ninguna de esas dos.
        LocalDate inicio = LocalDate.now().plusMonths(4);
        LocalDate entrega = inicio.plusMonths(1);
        modelReplies("""
                {"tasks":[{"title":"A"},{"title":"B"},{"title":"C"}],
                 "schedule":{"startDate":"%s","deadline":"%s","sprintDays":14}}""".formatted(inicio, entrega));

        var response = service.generate(7L, null, null);

        assertThat(response.schedule().startDate()).isEqualTo(inicio);
        assertThat(response.tasks()).allSatisfy(task -> {
            assertThat(task.dueDate()).isNotNull();
            assertThat(task.dueDate()).isAfterOrEqualTo(inicio);
            assertThat(task.dueDate()).isBeforeOrEqualTo(entrega);
        });
    }

    @Test
    @DisplayName("Un array suelto de tareas, sin objeto raiz, no puede traer schedule")
    void arrayShapeHasNoSchedule() {
        modelReplies("""
                [{"title":"Modelar el esquema"}]""");

        assertThat(service.generate(7L, null, null).schedule()).isNull();
    }

    @Test
    @DisplayName("El metodo de ayuda isEmpty() no se cuela en el JSON como si fuera un campo")
    void suggestedScheduleDoesNotLeakIsEmptyAsAField() throws Exception {
        var schedule = new AiPlanResponse.SuggestedSchedule(LocalDate.now(), null, null);
        // Igual que el ObjectMapper real de Spring Boot, que trae el soporte de
        // java.time ya registrado: aqui hay que anadirlo a mano.
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        String json = mapper.writeValueAsString(schedule);

        assertThat(json).doesNotContain("\"empty\"");
    }

    @Test
    @DisplayName("Acepta un array suelto y tambien las claves en espanol")
    void parsesAlternativeShapes() {
        modelReplies("""
                [{"titulo":"Escribir la memoria","motivo":"Lo pide el guion","tipo":"historia","prioridad":"BAJA","horas":8}]""");

        var tasks = service.generate(7L, null, null).tasks();

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).title()).isEqualTo("Escribir la memoria");
        assertThat(tasks.get(0).type()).isEqualTo(TaskType.HISTORIA);
        assertThat(tasks.get(0).priority()).isEqualTo(TaskPriority.BAJA);
        assertThat(tasks.get(0).estimatedHours()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("Descarta entradas sin titulo y normaliza prioridades y horas absurdas")
    void discardsUnusableEntries() {
        modelReplies("""
                {"tasks":[
                  {"title":"","reason":"sin titulo"},
                  {"title":"Valida","priority":"URGENTISIMA","estimatedHours":999},
                  {"title":"Otra valida","estimatedHours":-3}
                ]}""");

        var tasks = service.generate(7L, null, null).tasks();

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).priority()).isEqualTo(TaskPriority.MEDIA);
        assertThat(tasks.get(0).estimatedHours()).isNull();
        assertThat(tasks.get(1).estimatedHours()).isNull();
    }

    @Test
    @DisplayName("Descarta las tareas que el modelo repite pese a pedirle que no lo haga")
    void filtersDuplicatesOfExistingTasks() {
        Board board = Board.builder().id(3L).project(project).build();
        project.getBoards().add(board);
        BoardColumn column = BoardColumn.builder().id(10L).name("Por hacer").board(board).build();
        when(taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(3L)).thenReturn(List.of(
                Task.builder().id(1L).title("Escribir los tests de servicio").boardColumn(column).build(),
                Task.builder().id(2L).title("Preparar la presentacion").boardColumn(column).build()));

        modelReplies("""
                {"tasks":[
                  {"title":"Escribir tests de servicio"},
                  {"title":"Preparar la presentación"},
                  {"title":"Configurar la integracion continua"}
                ]}""");

        var tasks = service.generate(7L, null, null).tasks();

        // Se cuelan variantes con y sin tildes y con articulos distintos: se comparan normalizadas.
        assertThat(tasks).extracting(com.tfg.kanban.ai.dto.ProposedTask::title)
                .containsExactly("Configurar la integracion continua");
    }

    @Test
    @DisplayName("Respeta el maximo de tareas configurado")
    void respectsMaxTasks() {
        properties.setMaxTasks(2);
        modelReplies("""
                {"tasks":[{"title":"Una"},{"title":"Dos"},{"title":"Tres"},{"title":"Cuatro"}]}""");

        assertThat(service.generate(7L, null, null).tasks()).hasSize(2);
    }

    @Test
    @DisplayName("Una respuesta que no es una lista de tareas se reporta como IA no disponible")
    void rejectsUnusableResponse() {
        modelReplies("{\"respuesta\":\"lo siento, no puedo ayudarte\"}");

        assertThatThrownBy(() -> service.generate(7L, null, null))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("lista de tareas");
    }

    @Test
    @DisplayName("Un proyecto sin guion no llega siquiera a llamar al modelo")
    void requiresBriefing() {
        project.setBriefing("   ");

        assertThatThrownBy(() -> service.generate(7L, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("guion");

        org.mockito.Mockito.verifyNoInteractions(ollamaClient);
    }

    @Test
    @DisplayName("Con el modulo desactivado se avisa sin llamar al modelo")
    void respectsDisabledFlag() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.generate(7L, null, null))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("desactivado");

        org.mockito.Mockito.verifyNoInteractions(ollamaClient);
    }

    @Test
    @DisplayName("Aplicar crea las tareas aceptadas en la primera columna, marcadas como generadas por IA")
    void applyCreatesAcceptedTasks() {
        Board board = Board.builder().id(3L).project(project).build();
        BoardColumn primera = BoardColumn.builder().id(10L).name("Por hacer").position(0).board(board).build();
        BoardColumn segunda = BoardColumn.builder().id(11L).name("En curso").position(1).board(board).build();
        board.getColumns().addAll(List.of(segunda, primera));
        project.getBoards().add(board);

        when(boardService.getBoard(3L)).thenReturn(board);
        when(taskRepository.countByBoardColumn_Id(10L)).thenReturn(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).fullName("Ana").build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).fullName("Luis Perez").build()));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate fecha = LocalDate.now().plusDays(5);
        service.apply(7L, new ApplyPlanRequest(3L, List.of(
                new ProposedTask("Modelar el esquema", "porque si", TaskType.HISTORIA, TaskPriority.ALTA,
                        BigDecimal.valueOf(6), fecha, 2L, "Luis Perez"))));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());

        Task created = captor.getValue();
        assertThat(created.getTitle()).isEqualTo("Modelar el esquema");
        assertThat(created.isAiGenerated()).isTrue();
        assertThat(created.getBoardColumn()).isEqualTo(primera);
        assertThat(created.getPosition()).isEqualTo(2);
        assertThat(created.getType()).isEqualTo(TaskType.HISTORIA);
        assertThat(created.getDueDate()).isEqualTo(fecha);
        // El reparto llega en la peticion y se respeta, siempre que sea del equipo.
        assertThat(created.getAssignee()).isNotNull();
        assertThat(created.getAssignee().getId()).isEqualTo(2L);

        verify(activityService).record(any(), org.mockito.ArgumentMatchers.eq(ActivityAction.GENERACION_IA),
                anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Si la propuesta no trae tipo, la tarea creada cae en TAREA")
    void applyDefaultsMissingTypeToTarea() {
        Board board = Board.builder().id(3L).project(project).build();
        BoardColumn primera = BoardColumn.builder().id(10L).name("Por hacer").position(0).board(board).build();
        board.getColumns().add(primera);
        project.getBoards().add(board);

        when(boardService.getBoard(3L)).thenReturn(board);
        when(taskRepository.countByBoardColumn_Id(10L)).thenReturn(0L);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.apply(7L, new ApplyPlanRequest(3L, List.of(
                new ProposedTask("Tarea sin tipo", null, null, null, null, null, null, null))));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TaskType.TAREA);
        assertThat(captor.getValue().getDueDate()).isNull();
    }

    @Test
    @DisplayName("No se pueden aplicar tareas a un tablero de otro proyecto")
    void applyRejectsForeignBoard() {
        Project otro = Project.builder().id(99L).name("Otro").build();
        Board board = Board.builder().id(3L).project(otro).build();
        when(boardService.getBoard(3L)).thenReturn(board);

        var request = new ApplyPlanRequest(3L, List.of(
                new ProposedTask("Tarea", null, null, null, null, null, null, null)));

        assertThatThrownBy(() -> service.apply(7L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no pertenece");
    }
}
