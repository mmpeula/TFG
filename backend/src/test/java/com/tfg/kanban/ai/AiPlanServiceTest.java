package com.tfg.kanban.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfg.kanban.TestSecurity;
import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.ai.dto.ApplyPlanRequest;
import com.tfg.kanban.ai.dto.PlanOptions;
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
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Lo fragil del plan con IA no es la llamada HTTP sino lo que hay alrededor: que
 * al modelo le llegue lo que tiene que llegarle, interpretar lo que devuelve y
 * devolverselo con los errores cuando no cumple. Eso es lo que se prueba aqui.
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
    /** Un lunes: asi las fechas de fin de sprint caen en dias de la semana conocidos. */
    private LocalDate inicio;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAs(1L, "RESPONSABLE");

        properties = new AiProperties();
        properties.setModel("llama3");

        service = new AiPlanService(properties, ollamaClient, new ObjectMapper(), projectService,
                boardService, taskRepository, userRepository, activityService, taskMapper,
                new DocumentTextExtractor(), new AiTaskScheduler());

        inicio = LocalDate.now().plusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        Team team = Team.builder().id(1L).name("Grupo 4").build();
        team.getMembers().add(TeamMember.builder().id(1L).role(Role.RESPONSABLE)
                .team(team).user(User.builder().id(1L).fullName("Ana García").build()).build());
        team.getMembers().add(TeamMember.builder().id(2L).role(Role.MIEMBRO)
                .team(team).user(User.builder().id(2L).fullName("Luis Perez").build()).build());
        // El profesor sigue el equipo pero no es alumno: no entra en ningun reparto.
        team.getMembers().add(TeamMember.builder().id(3L).role(Role.DOCENTE)
                .team(team).user(User.builder().id(3L).fullName("Profesor Soto").build()).build());

        // Cuatro semanas justas: dos sprints de 14 dias.
        project = Project.builder()
                .id(7L).name("Practica 2").team(team)
                .briefing("Implementar una API REST de biblioteca con JWT.")
                .startDate(inicio).sprintDays(14).deadline(inicio.plusDays(27))
                .build();
        when(projectService.getProject(7L)).thenReturn(project);
        when(taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    /** Un plan que cumple todas las reglas: 2 personas x 2 sprints, nadie pasa de 20 h. */
    private static final String PLAN_VALIDO = """
            {"resumen":{"duracion_semanas":4,"num_sprints":2,
                        "carga_por_persona":[{"nombre":"Ana Garcia","horas":12,"tareas":2}]},
             "sprints":[{"numero":1,"objetivo":"Datos y API"},{"numero":2,"objetivo":"Interfaz y pruebas"}],
             "tareas":[
               {"titulo":"Disenar el modelo de datos","descripcion":"Tablas de libros y prestamos",
                "tipo":"Tarea","epica":"Datos","sprint":1,"asignado":"Ana Garcia","horas":6,
                "prioridad":"Alta","motivo":"El apartado 1 pide el modelo E/R","depende_de":[]},
               {"titulo":"Montar la API de prestamos","descripcion":"Endpoints REST de prestamos",
                "tipo":"Tarea","epica":"API","sprint":1,"asignado":"Luis Perez","horas":8,
                "prioridad":"Alta","motivo":"El apartado 2 pide la API","depende_de":["Disenar el modelo de datos"]},
               {"titulo":"Maquetar el catalogo","descripcion":"Pantalla con el listado de libros",
                "tipo":"Historia","epica":"Interfaz","sprint":2,"asignado":"Ana","horas":6,
                "prioridad":"Media","motivo":"El apartado 3 pide consultar el catalogo","depende_de":[]},
               {"titulo":"Escribir las pruebas de la API","descripcion":"Pruebas de integracion",
                "tipo":"Tarea","epica":"Calidad","sprint":2,"asignado":"Luis Perez","horas":5,
                "prioridad":"Media","motivo":"El apartado 4 pide pruebas","depende_de":[]}],
             "avisos":[]}""";

    /** El mismo plan, pero con Luis a 24 h en el sprint 1 cuando solo tiene 20. */
    private static final String PLAN_SOBRECARGADO = """
            {"resumen":{"duracion_semanas":4,"num_sprints":2},
             "tareas":[
               {"titulo":"Disenar el modelo de datos","sprint":1,"asignado":"Ana Garcia","horas":6},
               {"titulo":"Montar la API de prestamos","sprint":1,"asignado":"Luis Perez","horas":8},
               {"titulo":"Montar la API de socios","sprint":1,"asignado":"Luis Perez","horas":8},
               {"titulo":"Montar la API de autores","sprint":1,"asignado":"Luis Perez","horas":8},
               {"titulo":"Maquetar el catalogo","sprint":2,"asignado":"Ana Garcia","horas":6},
               {"titulo":"Escribir las pruebas de la API","sprint":2,"asignado":"Luis Perez","horas":5}],
             "avisos":[]}""";

    private void modelReplies(String first, String... rest) {
        when(ollamaClient.chatJson(anyString(), anyList())).thenReturn(first, rest);
    }

    /** Todas las conversaciones que se le han mandado al modelo, en orden. */
    @SuppressWarnings("unchecked")
    private List<List<OllamaClient.Message>> conversaciones() {
        ArgumentCaptor<List<OllamaClient.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(ollamaClient, atLeastOnce()).chatJson(anyString(), captor.capture());
        return captor.getAllValues();
    }

    /** El mensaje de usuario de la primera llamada: el que arma buildUserPrompt. */
    private String promptDeUsuario() {
        return conversaciones().get(0).get(0).content();
    }

    // -----------------------------------------------------------------
    // Lo que se le manda al modelo
    // -----------------------------------------------------------------

    @Test
    @DisplayName("El prompt de sistema es el fijo, palabra por palabra")
    void usesTheFixedSystemPrompt() {
        modelReplies(PLAN_VALIDO);

        service.generate(7L, null);

        verify(ollamaClient).chatJson(eq(AiPlanService.SYSTEM_PROMPT), anyList());
        assertThat(AiPlanService.SYSTEM_PROMPT)
                .startsWith("Eres un Project Manager senior y Scrum Master certificado")
                .endsWith("avisos:[]}");
    }

    @Test
    @DisplayName("El mensaje de usuario lleva calendario, equipo, indicaciones y documento")
    void userPromptCarriesEverything() {
        modelReplies(PLAN_VALIDO);

        service.generate(7L, new PlanOptions(null, null, "Ana tiene examenes la segunda semana", null));

        assertThat(promptDeUsuario())
                .contains("Fecha de inicio: " + inicio)
                .contains("Duracion del sprint: 14 dias (2 semanas)")
                .contains("- Sprint 1: " + inicio + " a " + inicio.plusDays(13))
                .contains("- Sprint 2: " + inicio.plusDays(14) + " a " + inicio.plusDays(27))
                .contains("EQUIPO (2 personas)")
                .contains("INDICACIONES ADICIONALES (solo para este plan)")
                .contains("Ana tiene examenes la segunda semana")
                .contains("DOCUMENTO DEL PROYECTO")
                .contains("Implementar una API REST de biblioteca con JWT.");
    }

    @Test
    @DisplayName("Cada persona llega con sus horas, su capacidad y sus preferencias")
    void teamLinesCarryTheFormData() {
        modelReplies(PLAN_VALIDO);

        service.generate(7L, new PlanOptions(null, null, null, List.of(
                new PlanOptions.TeamSlot(1L, 8, "Frontend; React y maquetacion"),
                new PlanOptions.TeamSlot(2L, 12, "Backend; Java y Spring"))));

        assertThat(promptDeUsuario())
                .contains("- Ana García (lider) | 8 h/semana | preferencias: Frontend; React y maquetacion")
                .contains("- Luis Perez | 12 h/semana | preferencias: Backend; Java y Spring")
                // Con sprints fijos, la capacidad va sprint a sprint.
                .contains("capacidad: Ana García 16 h, Luis Perez 24 h");
    }

    @Test
    @DisplayName("Sin preferencias en el formulario se usa la ficha de Miembros")
    void preferencesDefaultToTheMemberNotes() {
        project.getTeam().getMembers().get(1).setPlanNotes("Evitar frontend si hay alternativa");
        modelReplies(PLAN_VALIDO);

        service.generate(7L, null);

        assertThat(promptDeUsuario())
                .contains("- Luis Perez | 10 h/semana | preferencias: Evitar frontend si hay alternativa")
                .contains("preferencias: ninguna");
    }

    @Test
    @DisplayName("Quien se quita del formulario no entra en el reparto; el docente nunca entra")
    void onlyTheChosenTeamReachesTheModel() {
        modelReplies(PLAN_VALIDO);

        service.generate(7L, new PlanOptions(null, null, null, List.of(
                new PlanOptions.TeamSlot(2L, 10, null),
                // No es del equipo del reparto: el docente y alguien que no existe se ignoran.
                new PlanOptions.TeamSlot(3L, 10, null),
                new PlanOptions.TeamSlot(99L, 10, null))));

        assertThat(promptDeUsuario())
                .contains("EQUIPO (1 persona)")
                .contains("Luis Perez")
                .doesNotContain("Ana García")
                .doesNotContain("Profesor Soto")
                .doesNotContain("Fantasma");
    }

    @Test
    @DisplayName("Sin nadie en el reparto no se llama al modelo")
    void anEmptyTeamIsRejected() {
        var options = new PlanOptions(null, null, null, List.of(new PlanOptions.TeamSlot(3L, 10, null)));

        assertThatThrownBy(() -> service.generate(7L, options))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No hay nadie del equipo");
        verifyNoInteractions(ollamaClient);
    }

    @Test
    @DisplayName("Sin fecha de entrega, cuantos sprints hacen falta lo decide el modelo")
    void withoutDeadlineTheModelProposesTheDuration() {
        project.setDeadline(null);
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(promptDeUsuario()).contains("No hay fecha de entrega fijada").contains("resumen.num_sprints");
        assertThat(response.sprints()).hasSize(2);
        // Se ofrece guardar la entrega que sale del plan, para que la pestana Plan lo pinte igual.
        assertThat(response.schedule()).isNotNull();
        assertThat(response.schedule().deadline()).isEqualTo(inicio.plusDays(27));
    }

    // -----------------------------------------------------------------
    // Lo que se devuelve
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Un plan valido se interpreta entero a la primera")
    void parsesAValidPlan() {
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.attempts()).isEqualTo(1);
        assertThat(response.warnings()).isEmpty();
        assertThat(response.durationWeeks()).isEqualTo(4);
        assertThat(response.tasks()).hasSize(4);

        ProposedTask api = response.tasks().get(1);
        assertThat(api.title()).isEqualTo("Montar la API de prestamos");
        assertThat(api.description()).isEqualTo("Endpoints REST de prestamos");
        assertThat(api.reason()).isEqualTo("El apartado 2 pide la API");
        assertThat(api.type()).isEqualTo(TaskType.TAREA);
        assertThat(api.epic()).isEqualTo("API");
        assertThat(api.sprint()).isEqualTo(1);
        assertThat(api.priority()).isEqualTo(TaskPriority.ALTA);
        assertThat(api.estimatedHours()).isEqualByComparingTo("8");
        assertThat(api.dependsOn()).containsExactly("Disenar el modelo de datos");
        assertThat(response.tasks().get(2).type()).isEqualTo(TaskType.HISTORIA);
    }

    @Test
    @DisplayName("El nombre que da el modelo se resuelve contra el equipo, con o sin tildes y solo con el nombre")
    void resolvesAssigneesAgainstTheTeam() {
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        // "Ana Garcia" y "Ana" son la misma persona que "Ana García".
        assertThat(response.tasks().get(0).assigneeId()).isEqualTo(1L);
        assertThat(response.tasks().get(0).assigneeName()).isEqualTo("Ana García");
        assertThat(response.tasks().get(2).assigneeId()).isEqualTo(1L);
        assertThat(response.tasks().get(1).assigneeId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Los sprints los fecha el servidor y cada tarea cae el ultimo dia laborable del suyo")
    void datesComeFromTheServer() {
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.sprints()).extracting(s -> s.number()).containsExactly(1, 2);
        assertThat(response.sprints().get(0).start()).isEqualTo(inicio);
        assertThat(response.sprints().get(0).end()).isEqualTo(inicio.plusDays(13));
        assertThat(response.sprints().get(0).goal()).isEqualTo("Datos y API");
        // El sprint acaba en domingo: su ultimo dia laborable es el viernes anterior.
        assertThat(response.tasks().get(0).dueDate()).isEqualTo(inicio.plusDays(11));
        assertThat(response.tasks().get(3).dueDate()).isEqualTo(inicio.plusDays(25));
    }

    @Test
    @DisplayName("La barra de reparto cuenta las horas del plan, no lo que el modelo dice de si mismo")
    void workloadIsCountedFromTheTasks() {
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.workload()).hasSize(2);
        assertThat(response.workload().get(0).memberName()).isEqualTo("Ana García");
        assertThat(response.workload().get(0).tasks()).isEqualTo(2);
        assertThat(response.workload().get(0).hours()).isEqualByComparingTo("12");
        assertThat(response.workload().get(1).hours()).isEqualByComparingTo("13");
    }

    @Test
    @DisplayName("Los avisos del modelo llegan a la respuesta")
    void modelWarningsAreKept() {
        modelReplies(PLAN_VALIDO.replace("\"avisos\":[]", "\"avisos\":[\"El documento no dice la duracion\"]"));

        var response = service.generate(7L, null);

        assertThat(response.warnings()).containsExactly("El documento no dice la duracion");
    }

    @Test
    @DisplayName("Tambien se aceptan las claves en ingles, por si el modelo las cambia")
    void acceptsEnglishKeys() {
        modelReplies("""
                {"tasks":[
                  {"title":"Disenar el modelo","sprint":1,"assignee":"Ana","hours":"6h","priority":"high","type":"story"},
                  {"title":"Montar la API","sprint":1,"assignee":"Luis","hours":6},
                  {"title":"Maquetar","sprint":"Sprint 2","assignee":"Ana","hours":6},
                  {"title":"Probar","sprint":2,"assignee":"Luis","hours":6}]}""");

        var response = service.generate(7L, null);

        assertThat(response.warnings()).isEmpty();
        assertThat(response.tasks().get(0).estimatedHours()).isEqualByComparingTo("6");
        assertThat(response.tasks().get(0).priority()).isEqualTo(TaskPriority.ALTA);
        assertThat(response.tasks().get(0).type()).isEqualTo(TaskType.HISTORIA);
        assertThat(response.tasks().get(2).sprint()).isEqualTo(2);
    }

    @Test
    @DisplayName("Se descartan las tareas que ya estan en el tablero")
    void dropsTasksAlreadyOnTheBoard() {
        project.getBoards().add(Board.builder().id(3L).project(project).build());
        when(taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(3L))
                .thenReturn(List.of(Task.builder().title("Escribir las pruebas de la API").build()));
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(promptDeUsuario()).contains("YA ESTA EN EL TABLERO");
        assertThat(response.tasks()).extracting(ProposedTask::title)
                .doesNotContain("Escribir las pruebas de la API");
    }

    // -----------------------------------------------------------------
    // Validar y reintentar
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Si el plan no cumple, se le devuelve al modelo con los errores y se usa el corregido")
    void retriesWithConcreteErrors() {
        modelReplies(PLAN_SOBRECARGADO, PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.attempts()).isEqualTo(2);
        assertThat(response.warnings()).isEmpty();
        assertThat(response.tasks()).hasSize(4);

        assertThat(conversaciones()).hasSize(2);
        List<OllamaClient.Message> correccion = conversaciones().get(1);
        assertThat(correccion).hasSize(3);
        assertThat(correccion.get(1).role()).isEqualTo("assistant");
        assertThat(correccion.get(1).content()).isEqualTo(PLAN_SOBRECARGADO);
        assertThat(correccion.get(2).content())
                .contains("Luis Perez tiene 24 h en el sprint 1 y su capacidad es 20 h: quita o mueve 4 h.")
                .contains("devuelve el plan COMPLETO");
    }

    @Test
    @DisplayName("Tras los reintentos, lo que siga sin cumplirse se muestra como aviso")
    void keepsTheLastPlanWithItsErrorsAsWarnings() {
        modelReplies(PLAN_SOBRECARGADO);

        var response = service.generate(7L, null);

        // Uno mas los dos reintentos configurados por defecto.
        assertThat(response.attempts()).isEqualTo(3);
        assertThat(response.tasks()).hasSize(6);
        assertThat(response.warnings())
                .anySatisfy(aviso -> assertThat(aviso).contains("sigue sin cumplir"))
                .anySatisfy(aviso -> assertThat(aviso).contains("Luis Perez tiene 24 h en el sprint 1"));
    }

    @Test
    @DisplayName("Un JSON roto tambien se reintenta")
    void retriesAfterBrokenJson() {
        modelReplies("esto no es json", PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.attempts()).isEqualTo(2);
        assertThat(conversaciones().get(1).get(2).content()).contains("no era JSON valido");
    }

    @Test
    @DisplayName("Si nunca llega un plan que se pueda leer, se avisa de que la IA no ha respondido bien")
    void failsWhenNoAttemptIsReadable() {
        modelReplies("nada", "{\"sin\":\"tareas\"}", "tampoco");

        assertThatThrownBy(() -> service.generate(7L, null))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("tras 3 intentos");
    }

    @Test
    @DisplayName("Los reintentos se pueden configurar")
    void retriesAreConfigurable() {
        properties.setMaxRetries(0);
        modelReplies(PLAN_SOBRECARGADO);

        var response = service.generate(7L, null);

        assertThat(response.attempts()).isEqualTo(1);
        assertThat(response.warnings()).isNotEmpty();
    }

    // -----------------------------------------------------------------
    // Entradas y bordes
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Un documento subido se guarda como guion y es lo que se le manda al modelo")
    void documentBecomesTheBriefing() {
        modelReplies(PLAN_VALIDO);
        var file = new MockMultipartFile("file", "guion.txt", "text/plain",
                "Practica de gestion de una biblioteca".getBytes(StandardCharsets.UTF_8));

        var response = service.generateFromDocument(7L, file, null);

        assertThat(project.getBriefing()).isEqualTo("Practica de gestion de una biblioteca");
        assertThat(response.source()).isEqualTo("documento: guion.txt");
        assertThat(promptDeUsuario()).contains("Practica de gestion de una biblioteca");
    }

    @Test
    @DisplayName("Un proyecto sin guion no llega siquiera a llamar al modelo")
    void requiresABriefing() {
        project.setBriefing("  ");

        assertThatThrownBy(() -> service.generate(7L, null))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(ollamaClient);
    }

    @Test
    @DisplayName("Una descripcion escrita vacia se rechaza antes de llamar al modelo")
    void requiresADescription() {
        assertThatThrownBy(() -> service.generateFromText(7L, "   ", null))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(ollamaClient);
    }

    @Test
    @DisplayName("Con el modulo desactivado se avisa sin llamar al modelo")
    void refusesWhenDisabled() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.generate(7L, null))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("desactivado");
        verifyNoInteractions(ollamaClient);
    }

    // -----------------------------------------------------------------
    // Aplicar al tablero
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Aplicar crea las tareas aceptadas con su epica, descripcion y motivo")
    void applyCreatesTheAcceptedTasks() {
        Board board = Board.builder().id(3L).project(project).build();
        BoardColumn primera = BoardColumn.builder().id(10L).name("Por hacer").position(0).board(board).build();
        board.getColumns().add(primera);
        when(boardService.getBoard(3L)).thenReturn(board);
        when(taskRepository.countByBoardColumn_Id(10L)).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).fullName("Luis Perez").build()));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate fecha = inicio.plusDays(11);
        service.apply(7L, new ApplyPlanRequest(3L, List.of(
                new ProposedTask("Montar la API", "Endpoints REST", "Lo pide el apartado 2", TaskType.HISTORIA,
                        "API", 1, TaskPriority.ALTA, BigDecimal.valueOf(6), fecha, 2L, "Luis Perez", List.of()))));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task created = captor.getValue();
        assertThat(created.getTitle()).isEqualTo("Montar la API");
        assertThat(created.getDescription()).isEqualTo("Endpoints REST\n\nMotivo: Lo pide el apartado 2");
        assertThat(created.getEpic()).isEqualTo("API");
        assertThat(created.getType()).isEqualTo(TaskType.HISTORIA);
        assertThat(created.getDueDate()).isEqualTo(fecha);
        assertThat(created.isAiGenerated()).isTrue();
        assertThat(created.getPosition()).isEqualTo(2);
        assertThat(created.getAssignee().getId()).isEqualTo(2L);
        verify(activityService).record(any(), eq(ActivityAction.GENERACION_IA), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Sin tipo la tarea cae en TAREA, y solo se asigna a gente del equipo")
    void applyDefaultsAndGuardsTheAssignee() {
        Board board = Board.builder().id(3L).project(project).build();
        board.getColumns().add(BoardColumn.builder().id(10L).name("Por hacer").position(0).board(board).build());
        when(boardService.getBoard(3L)).thenReturn(board);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.apply(7L, new ApplyPlanRequest(3L, List.of(
                new ProposedTask("Tarea", null, null, null, null, null, null, null, null, 99L, "Nadie", null))));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TaskType.TAREA);
        assertThat(captor.getValue().getAssignee()).isNull();
        assertThat(captor.getValue().getEpic()).isNull();
    }

    @Test
    @DisplayName("No se pueden aplicar tareas a un tablero de otro proyecto")
    void applyRejectsForeignBoard() {
        Board board = Board.builder().id(3L).project(Project.builder().id(99L).name("Otro").build()).build();
        when(boardService.getBoard(3L)).thenReturn(board);

        var request = new ApplyPlanRequest(3L, List.of(
                new ProposedTask("Tarea", null, null, null, null, null, null, null, null, null, null, null)));

        assertThatThrownBy(() -> service.apply(7L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no pertenece");
    }
}
