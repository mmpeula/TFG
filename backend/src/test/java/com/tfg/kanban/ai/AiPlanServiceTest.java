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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * El documento se manda entero en una sola llamada a Groq: lo fragil no es la
 * llamada HTTP en si, sino que al modelo le llegue el calendario y el equipo
 * bien armados, que su respuesta se interprete entera (tareas, objetivos de
 * sprint, dependencias), y que lo medible —horas, dependencias validas— se
 * compruebe en Java, no en su palabra. Eso es lo que se prueba aqui.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiPlanServiceTest {

    @Mock private GroqClient groqClient;
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
        properties.setModel("openai/gpt-oss-120b");
        properties.setMaxRetries(1);

        service = new AiPlanService(properties, groqClient, new ObjectMapper(), projectService,
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

    /**
     * Un plan valido de dos sprints: Ana y Luis, con objetivo por sprint y una
     * dependencia hacia atras valida (la de Luis en el sprint 2 depende de su
     * propia tarea del sprint 1). Las fechas de "sprints"/"fecha" son
     * deliberadamente absurdas: sirven para comprobar que el servidor las ignora
     * y pone las suyas.
     */
    private static final String PLAN_VALIDO = """
            {"resumen":{"duracion_semanas":4,"num_sprints":2},
             "sprints":[
               {"numero":1,"inicio":"2099-01-01","fin":"2099-01-01","objetivo":"Datos y API","fecha_definitiva":true},
               {"numero":2,"inicio":"2099-01-01","fin":"2099-01-01","objetivo":"Interfaz y pruebas","fecha_definitiva":true}
             ],
             "tareas":[
               {"titulo":"Disenar el modelo de datos","descripcion":"Tablas de libros y prestamos",
                "tipo":"Task","epica":"Datos","sprint":1,"asignado":"Ana Garcia","horas":7,
                "prioridad":"alta","fecha":"2099-01-01","motivo":"El apartado 1 pide el modelo E/R","depende_de":[]},
               {"titulo":"Montar la API de prestamos","descripcion":"Endpoints REST de prestamos",
                "tipo":"Task","epica":"API","sprint":1,"asignado":"Luis Perez","horas":8,
                "prioridad":"alta","motivo":"El apartado 2 pide la API","depende_de":["Disenar el modelo de datos"]},
               {"titulo":"Maquetar el catalogo","descripcion":"Pantalla con el listado de libros",
                "tipo":"Story","epica":"Interfaz","sprint":2,"asignado":"Ana","horas":6,
                "prioridad":"media","motivo":"El apartado 3 pide consultar el catalogo","depende_de":[]},
               {"titulo":"Escribir las pruebas de la API","descripcion":"Pruebas de integracion",
                "tipo":"Task","epica":"Calidad","sprint":2,"asignado":"Luis Perez","horas":5,
                "prioridad":"media","motivo":"El apartado 4 pide pruebas","depende_de":["Montar la API de prestamos"]}],
             "avisos":[]}""";

    private void modelReplies(String first, String... rest) {
        when(groqClient.chatJson(eq(AiPlanService.SYSTEM_PROMPT), anyString())).thenReturn(first, rest);
    }

    /** El mensaje de usuario de la primera llamada. */
    private String promptDeUsuario() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(groqClient, atLeastOnce()).chatJson(eq(AiPlanService.SYSTEM_PROMPT), captor.capture());
        return captor.getAllValues().get(0);
    }

    // -----------------------------------------------------------------
    // Lo que se le manda al modelo
    // -----------------------------------------------------------------

    @Test
    @DisplayName("El prompt de sistema es el fijo y se llama una sola vez")
    void usesTheFixedSystemPromptOnce() {
        modelReplies(PLAN_VALIDO);

        service.generate(7L, null);

        verify(groqClient, times(1)).chatJson(eq(AiPlanService.SYSTEM_PROMPT), anyString());
        assertThat(AiPlanService.SYSTEM_PROMPT)
                .startsWith("Eres un Scrum Master y Project Manager senior")
                .contains("REGLAS DE REPARTO (por prioridad")
                .contains("capacidad útil")
                .contains("FORMATO DE SALIDA")
                .contains("depende_de");
    }

    @Test
    @DisplayName("El formato de salida no pide las fechas: las pone el servidor y gastarian cuota")
    void theOutputFormatSkipsWhatTheServerComputes() {
        assertThat(AiPlanService.SYSTEM_PROMPT)
                .doesNotContain("YYYY-MM-DD")
                .doesNotContain("carga_por_persona")
                .doesNotContain("duracion_semanas");
    }

    @Test
    @DisplayName("El mensaje de usuario lleva calendario, equipo y el documento completo, no un fragmento")
    void userPromptCarriesEverything() {
        modelReplies(PLAN_VALIDO);

        service.generate(7L, null);

        assertThat(promptDeUsuario())
                .contains("Fecha de inicio: " + inicio)
                .contains("Duracion del sprint: 14 dias (2 semanas)")
                .contains("Fecha de entrega: " + inicio.plusDays(27))
                .contains("- Sprint 1: " + inicio + " a " + inicio.plusDays(13))
                .contains("- Sprint 2: " + inicio.plusDays(14) + " a " + inicio.plusDays(27))
                .contains("EQUIPO (2 personas)")
                .contains("DOCUMENTO DEL PROYECTO")
                .contains("Implementar una API REST de biblioteca con JWT.");
    }

    @Test
    @DisplayName("Cada persona llega con sus horas, sus preferencias y su capacidad util ya calculada")
    void teamLinesCarryRoleAndCapacity() {
        modelReplies(PLAN_VALIDO);

        service.generate(7L, new PlanOptions(null, null, null, List.of(
                new PlanOptions.TeamSlot(1L, 8, "Frontend; React y maquetacion"),
                new PlanOptions.TeamSlot(2L, 12, "Backend; Java y Spring"))));

        // 8 y 12 h/semana en 14 dias son 16 y 24 h; sin el 10 % de margen, 14.4 y 21.6.
        assertThat(promptDeUsuario())
                .contains("- Ana García (lider) | 8 h/semana | rol y preferencias: Frontend; React y maquetacion")
                .contains("- Luis Perez | 12 h/semana | rol y preferencias: Backend; Java y Spring")
                .contains("capacidad util: Ana García 14.4 h, Luis Perez 21.6 h (equipo 36 h)");
    }

    @Test
    @DisplayName("Sin fecha de entrega, cada persona lleva su capacidad util por sprint")
    void withoutDeadlineEachPersonCarriesItsCapacityPerSprint() {
        project.setDeadline(null);
        modelReplies(PLAN_VALIDO);

        service.generate(7L, null);

        assertThat(promptDeUsuario())
                .contains("- Luis Perez | 10 h/semana | capacidad util: 18 h por sprint | rol y preferencias");
    }

    @Test
    @DisplayName("Quien tiene 0 h llega marcado como no disponible")
    void zeroHoursIsMarkedAsUnavailable() {
        modelReplies(PLAN_VALIDO);

        service.generate(7L, new PlanOptions(null, null, null, List.of(
                new PlanOptions.TeamSlot(1L, 10, null),
                new PlanOptions.TeamSlot(2L, 0, null))));

        assertThat(promptDeUsuario()).contains("- Luis Perez | 0 h/semana (no disponible) | rol y preferencias");
    }

    @Test
    @DisplayName("Sin preferencias en el formulario se usa la ficha de Miembros")
    void preferencesDefaultToTheMemberNotes() {
        project.getTeam().getMembers().get(1).setPlanNotes("Evitar frontend si hay alternativa");
        modelReplies(PLAN_VALIDO);

        service.generate(7L, null);

        assertThat(promptDeUsuario())
                .contains("Luis Perez | 10 h/semana | rol y preferencias: Evitar frontend si hay alternativa")
                .contains("Ana García (lider) | 10 h/semana | rol y preferencias: sin especificar");
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
        verifyNoInteractions(groqClient);
    }

    @Test
    @DisplayName("Sin fecha de entrega, el propio plan dice cuantos sprints hacen falta, sin otra llamada")
    void withoutDeadlineTheModelProposesTheDuration() {
        project.setDeadline(null);
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(promptDeUsuario()).contains("No hay fecha de entrega fijada").contains("resumen.num_sprints");
        verify(groqClient, times(1)).chatJson(anyString(), anyString());
        assertThat(response.sprints()).hasSize(2);
        assertThat(response.sprints().get(1).end()).isEqualTo(inicio.plusDays(27));
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
        assertThat(response.tasks().get(2).type()).isEqualTo(TaskType.HISTORIA);
    }

    @Test
    @DisplayName("El objetivo de cada sprint viene del modelo (\"goal\"), no se deja vacio")
    void sprintGoalComesFromTheModel() {
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.sprints().get(0).goal()).isEqualTo("Datos y API");
        assertThat(response.sprints().get(1).goal()).isEqualTo("Interfaz y pruebas");
    }

    @Test
    @DisplayName("Una dependencia valida (misma tarea o sprint anterior) se conserva en dependsOn")
    void validDependenciesAreKept() {
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.tasks().get(1).dependsOn()).containsExactly("Disenar el modelo de datos");
        assertThat(response.tasks().get(3).dependsOn()).containsExactly("Montar la API de prestamos");
        assertThat(response.warnings()).noneMatch(aviso -> aviso.contains("descartado"));
    }

    @Test
    @DisplayName("Una dependencia hacia un sprint posterior se descarta y se avisa, sin tirar la tarea")
    void forwardDependencyIsDropped() {
        modelReplies(PLAN_VALIDO.replace(
                "\"motivo\":\"El apartado 1 pide el modelo E/R\",\"depende_de\":[]",
                "\"motivo\":\"El apartado 1 pide el modelo E/R\",\"depende_de\":[\"Maquetar el catalogo\"]"));

        var response = service.generate(7L, null);

        ProposedTask disenar = response.tasks().get(0);
        assertThat(disenar.dependsOn()).isEmpty();
        assertThat(response.warnings()).anySatisfy(aviso -> assertThat(aviso)
                .contains("descartado una dependencia")
                .contains("Disenar el modelo de datos")
                .contains("Maquetar el catalogo"));
    }

    @Test
    @DisplayName("Una dependencia hacia una tarea que no existe tambien se descarta y se avisa")
    void dependencyOnAMissingTaskIsDropped() {
        modelReplies(PLAN_VALIDO.replace("\"depende_de\":[\"Disenar el modelo de datos\"]",
                "\"depende_de\":[\"Tarea que no existe\"]"));

        var response = service.generate(7L, null);

        assertThat(response.tasks().get(1).dependsOn()).isEmpty();
        assertThat(response.warnings()).anySatisfy(aviso -> assertThat(aviso)
                .contains("descartado una dependencia")
                .contains("Tarea que no existe"));
    }

    @Test
    @DisplayName("Si el modelo marca un sprint sin fecha definitiva, se avisa")
    void notDefinitiveSprintDateBecomesAWarning() {
        modelReplies(PLAN_VALIDO.replace(
                "\"objetivo\":\"Interfaz y pruebas\",\"fecha_definitiva\":true",
                "\"objetivo\":\"Interfaz y pruebas\",\"fecha_definitiva\":false"));

        var response = service.generate(7L, null);

        assertThat(response.warnings()).anySatisfy(aviso -> assertThat(aviso)
                .contains("sprint 2").contains("no tiene fecha exacta confirmada"));
    }

    @Test
    @DisplayName("El nombre que da el modelo se resuelve contra el equipo, con o sin tildes y solo con el nombre")
    void resolvesAssigneesAgainstTheTeam() {
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        // "Ana Garcia" y "Ana" son la misma persona que "Ana García".
        assertThat(response.tasks().get(0).assigneeIds()).containsExactly(1L);
        assertThat(response.tasks().get(0).assigneeNames()).containsExactly("Ana García");
        assertThat(response.tasks().get(2).assigneeIds()).containsExactly(1L);
        assertThat(response.tasks().get(1).assigneeIds()).containsExactly(2L);
    }

    @Test
    @DisplayName("El modelo puede asignar la misma tarea a varias personas cuando lo decide")
    void resolvesMultipleAssigneesAgainstTheTeam() {
        modelReplies(PLAN_VALIDO.replace(
                "\"asignado\":\"Ana Garcia\",\"horas\":7,",
                "\"asignado\":[\"Ana Garcia\",\"Luis Perez\"],\"horas\":7,"));

        var response = service.generate(7L, null);

        assertThat(response.tasks().get(0).assigneeIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(response.tasks().get(0).assigneeNames()).containsExactlyInAnyOrder("Ana García", "Luis Perez");
    }

    @Test
    @DisplayName("Tambien se acepta un array bajo la clave en plural \"asignados\"")
    void resolvesAssigneesUnderThePluralKey() {
        modelReplies(PLAN_VALIDO.replace(
                "\"asignado\":\"Ana Garcia\"", "\"asignados\":[\"Ana Garcia\",\"Luis Perez\"]"));

        var response = service.generate(7L, null);

        assertThat(response.tasks().get(0).assigneeIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("Un nombre del array que no esta en el equipo conserva el texto crudo, sin id")
    void keepsUnmatchedAssigneeNameAsRawText() {
        modelReplies(PLAN_VALIDO.replace(
                "\"asignado\":\"Ana Garcia\",\"horas\":7,",
                "\"asignado\":[\"Ana Garcia\",\"Fantasma\"],\"horas\":7,"));

        var response = service.generate(7L, null);

        assertThat(response.tasks().get(0).assigneeIds()).containsExactly(1L);
        assertThat(response.tasks().get(0).assigneeNames()).containsExactly("Ana García", "Fantasma");
    }

    @Test
    @DisplayName("Los sprints los fecha el servidor y cada tarea cae el ultimo dia laborable del suyo, "
            + "ignorando las fechas que diga el modelo")
    void datesComeFromTheServerNotFromTheModel() {
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.sprints()).extracting(s -> s.number()).containsExactly(1, 2);
        assertThat(response.sprints().get(0).start()).isEqualTo(inicio);
        assertThat(response.sprints().get(0).end()).isEqualTo(inicio.plusDays(13));
        // El sprint acaba en domingo: su ultimo dia laborable es el viernes anterior.
        assertThat(response.tasks().get(0).dueDate()).isEqualTo(inicio.plusDays(11));
        assertThat(response.tasks().get(3).dueDate()).isEqualTo(inicio.plusDays(25));
        // El plan mockeado pone "2099-01-01" en sprints e "inicio"/"fin"/"fecha": si algo de
        // esto se colara, las fechas de arriba no cuadrarian.
        assertThat(response.sprints().get(0).start()).isNotEqualTo(LocalDate.of(2099, 1, 1));
    }

    @Test
    @DisplayName("La barra de reparto cuenta las horas del plan, no lo que el modelo dice de si mismo")
    void workloadIsCountedFromTheTasks() {
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.workload()).hasSize(2);
        assertThat(response.workload().get(0).memberName()).isEqualTo("Ana García");
        assertThat(response.workload().get(0).tasks()).isEqualTo(2);
        assertThat(response.workload().get(0).hours()).isEqualByComparingTo("13");
        assertThat(response.workload().get(1).hours()).isEqualByComparingTo("13");
    }

    @Test
    @DisplayName("Una tarea con dos responsables cuenta las horas completas para cada uno, no repartidas")
    void hourBalanceCountsFullHoursForEachCoAssignee() {
        modelReplies("""
                {"tareas":[{"titulo":"Disenar la autenticacion","sprint":1,
                  "asignado":["Ana Garcia","Luis Perez"],"horas":6}]}""");

        var response = service.generate(7L, null);

        assertThat(response.workload()).hasSize(2);
        assertThat(response.workload()).allSatisfy(workload -> assertThat(workload.hours()).isEqualByComparingTo("6"));
    }

    @Test
    @DisplayName("Cada persona lleva en el resultado la capacidad de todo el plan, para comparar con sus horas")
    void workloadCarriesTheCapacityOfThePlan() {
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        // 10 h/semana por defecto, dos sprints de 14 dias: 20 + 20.
        assertThat(response.workload()).allSatisfy(workload ->
                assertThat(workload.capacityHours()).isEqualByComparingTo("40"));
    }

    /** Un plan de un solo sprint con las horas dadas para Ana y Luis. */
    private static String unSprint(int horasAna, int horasLuis) {
        return """
                {"tareas":[
                  {"titulo":"Trabajo de Ana","sprint":1,"asignado":"Ana Garcia","horas":%d},
                  {"titulo":"Trabajo de Luis","sprint":1,"asignado":"Luis Perez","horas":%d}]}"""
                .formatted(horasAna, horasLuis);
    }

    @Test
    @DisplayName("Si alguien pasa de su capacidad en un sprint, se avisa")
    void overloadIsWarned() {
        modelReplies(unSprint(24, 10));

        var response = service.generate(7L, null);

        assertThat(response.warnings()).anySatisfy(aviso -> assertThat(aviso)
                .contains("sprint 1").contains("Ana García tiene 24 h y solo dispone de 20 h"));
    }

    @Test
    @DisplayName("Horas distintas en proporcion a la disponibilidad de cada uno no son un desequilibrio")
    void proportionalHoursAreNotAnImbalance() {
        modelReplies(unSprint(8, 24));

        // 5 y 15 h/semana: 10 y 30 h por sprint. 8 y 24 son un 80 % para los dos.
        var response = service.generate(7L, new PlanOptions(null, null, null, List.of(
                new PlanOptions.TeamSlot(1L, 5, null),
                new PlanOptions.TeamSlot(2L, 15, null))));

        assertThat(response.warnings()).noneMatch(aviso -> aviso.contains("revisa el reparto"));
    }

    @Test
    @DisplayName("Si alguien usa mucho menos de sus horas que quien mas usa las suyas, se avisa")
    void relativeImbalanceIsWarned() {
        modelReplies(unSprint(18, 4));

        var response = service.generate(7L, null);

        assertThat(response.warnings()).anySatisfy(aviso -> assertThat(aviso)
                .contains("Luis Perez usa el 20% de sus horas frente al 90% de Ana García"));
    }

    @Test
    @DisplayName("Quien se queda sin trabajo en un sprint sin que el modelo lo explique se avisa")
    void idleWithoutExplanationIsWarned() {
        modelReplies("""
                {"tareas":[{"titulo":"Trabajo de Ana","sprint":1,"asignado":"Ana Garcia","horas":18}]}""");

        var response = service.generate(7L, null);

        assertThat(response.warnings()).anySatisfy(aviso -> assertThat(aviso)
                .contains("Luis Perez usa el 0% de sus horas"));
    }

    @Test
    @DisplayName("Si el modelo explica que alguien no esta en un sprint (vacaciones), no se le contradice")
    void idleExplainedByTheModelIsNotWarnedAgain() {
        modelReplies("""
                {"tareas":[{"titulo":"Trabajo de Ana","sprint":1,"asignado":"Ana Garcia","horas":18}],
                 "avisos":["Luis Pérez no está disponible en el sprint 1 por vacaciones"]}""");

        var response = service.generate(7L, null);

        assertThat(response.warnings()).noneMatch(aviso -> aviso.contains("usa el 0%"));
    }

    @Test
    @DisplayName("Si alguien sin horas disponibles recibe tareas, se avisa")
    void tasksForSomeoneWithoutHoursAreWarned() {
        modelReplies(unSprint(8, 4));

        var response = service.generate(7L, new PlanOptions(null, null, null, List.of(
                new PlanOptions.TeamSlot(1L, 10, null),
                new PlanOptions.TeamSlot(2L, 0, null))));

        assertThat(response.warnings()).anySatisfy(aviso -> assertThat(aviso)
                .contains("Luis Perez no tiene horas disponibles y se le ha asignado 1 tarea"));
    }

    @Test
    @DisplayName("Los avisos que pone el propio modelo llegan a la respuesta")
    void modelWarningsAreKept() {
        modelReplies(PLAN_VALIDO.replace("\"avisos\":[]", "\"avisos\":[\"El documento no dice la duracion\"]"));

        var response = service.generate(7L, null);

        assertThat(response.warnings()).contains("El documento no dice la duracion");
    }

    @Test
    @DisplayName("Un criterio detectado sin ninguna tarea relacionada se avisa, no se deja pasar en silencio")
    void criterionWithoutAnyRelatedTaskGetsAWarning() {
        modelReplies(PLAN_VALIDO.replace("\"duracion_semanas\":4,\"num_sprints\":2}",
                "\"duracion_semanas\":4,\"num_sprints\":2,\"criterios_detectados\":["
                        + "{\"criterio\":\"Presentacion final\",\"peso_o_importancia\":\"1 punto\","
                        + "\"tareas_relacionadas\":[]}]}"));

        var response = service.generate(7L, null);

        assertThat(response.warnings()).anySatisfy(aviso -> assertThat(aviso)
                .contains("Presentacion final")
                .contains("no tiene ninguna tarea asociada"));
    }

    @Test
    @DisplayName("Si todos los criterios detectados tienen alguna tarea relacionada, no hay aviso de cobertura")
    void criteriaFullyCoveredProduceNoCoverageWarning() {
        modelReplies(PLAN_VALIDO.replace("\"duracion_semanas\":4,\"num_sprints\":2}",
                "\"duracion_semanas\":4,\"num_sprints\":2,\"criterios_detectados\":["
                        + "{\"criterio\":\"Modelo de datos\",\"peso_o_importancia\":\"2 puntos\","
                        + "\"tareas_relacionadas\":[\"Disenar el modelo de datos\"]}]}"));

        var response = service.generate(7L, null);

        assertThat(response.warnings()).noneMatch(aviso -> aviso.contains("no tiene ninguna tarea asociada"));
    }

    /**
     * Texto real de un guion de practica de agentes con JADE: 8445 caracteres,
     * con la rubrica de evaluacion (0.5/0.5/1/2/2/3/1 puntos) empezando en el
     * caracter 7137, muy por encima de DOCUMENT_CHARS_FOR_MODEL (6000). Sin el
     * detector de criterios, esa rubrica quedaria fuera de lo que ve el modelo
     * por el simple recorte de longitud: es justo el caso que reprodujo el bug.
     */
    private static final String DOCUMENTO_JADE = """
            Práctica 2. Movimiento de una
            agente en un mundo
            bidimensional
            DISEÑO BASADO EN AGENTES CURSO 2025/2026

            MANUEL JESÚS COBO MARTÍN
            DEPARTAMENTO DE CIENCIAS DE LA COMUTACIÓN E INTELIGENCIA ARTIFICIAL |
            UNIVERSIDAD DE GRANADA

            1 Contexto
            En la práctica anterior hemos visto el funcionamiento global de JADE, desde cómo lanzar el
            servidor hasta cómo crear un agente, añadirle diferentes tipos de comportamientos y ejecutar el
            agente en el servidor.
            Estos conocimientos son básicos a la hora de crear un sistema multiagentes, y los usaremos para
            el desarrollo de las siguientes prácticas.
            En esta práctica, desarrollaremos un agente inteligente que sea capaz de desplazarse por un mundo
            de dos dimensiones, evitando obstáculos y dirigiéndose hacia una posición objetivo. Usando la
            terminología explicada en clase, el agente que implementaremos será reactivo con estados
            internos.
            La práctica se realizará en grupos de 4 personas, salvo excepciones debidamente justificadas.
            Todos los componentes del equipo tienen que trabajar por igual de forma equitativa. Cabe resaltar
            que todos los componentes no tienen por qué tener la misma calificación, en los casos en los que
            se estime que su contribución al grupo ha sido menor, la calificación podrá ser diferente.

            2 Descripción de la práctica
            En esta práctica se desarrollará un agente autónomo capaz de desplazarse por un mundo
            bidimensional evitando obstáculos y gestionando su energía.
            El objetivo principal es que el estudiante diseñe una arquitectura que respete el ciclo clásico de
            percepción → decisión → acción, donde el agente observa el entorno a través de sensores, decide
            qué hacer en función de su percepción y comunica su decisión al entorno para que este la ejecute.
            De este modo, el agente deberá ser capaz de percibir el estado inmediato de su entorno, decidir
            cuál es la mejor acción en cada situación y actuar sobre el mundo de forma controlada.
            El entorno, por su parte, será el responsable de mantener el estado del mundo, ejecutar las acciones
            y validar que se cumplan las restricciones del sistema.
            No se proporcionará ninguna arquitectura base: el diseño será completamente responsabilidad del
            estudiante. La evaluación valorará no solo el correcto funcionamiento del sistema, sino también
            la coherencia estructural del modelo, el respeto a los principios de encapsulación y la claridad de
            la separación de responsabilidades.
            El mundo estará representado mediante una matriz bidimensional de tamaño ancho × alto, cargada
            desde un fichero de texto plano. Así, el mundo se representará como una matriz de tamaño NxM
            formada por números enteros. En cada celda habrá un valor numérico que nos determinará si la
            celda se encuentra vacía, o hay un obstáculo por lo que el agente no podría situarse sobre esa
            celda. De este modo, un valor de -1 indica que la celda no es accesible y un valor de 0 indica que
            la celda está libre. El mapa lo almacenaremos como un fichero de texto, en el que cada línea será
            una fila de la matriz, estando las columnas delimitadas por tabulaciones. Para poder leer de forma
            correcta el mapa, necesitamos saber las dimensiones de este, por lo que la primera línea
            representará el número de filas (ancho) y la segunda línea representará el número de columnas
            (alto). Así, por ejemplo, un mapa sencillo podría representarse de la siguiente manera:

            -1-

            Figura 1. Fichero de texto conteniendo un mapa de 10x10 con dos obstáculos

            Hemos de señalar que en el fichero que representa al mapa no se guarda la posición inicial del
            agente, ni la posición del objetivo. Dichas posiciones iniciales deben indicarse en la configuración
            del agente, bien desde código o bien a través de argumentos. De este modo se podrá ejecutar el
            agente desde diferentes posiciones, para ver si es capaz de llegar siempre al objetivo.
            Las posiciones del agente se representarán mediante coordenadas (x, y), donde X corresponde a
            la columna (0 = izquierda) e Y a la fila (0 = arriba).
            El agente podrá ejecutar cuatro movimientos básicos:
            ACCIÓN
            UP
            DOWN
            LEFT
            RIGHT

            EFECTO SOBRE LA POSICIÓN
            (x, y-1)
            (x, y+1)
            (x-1, y)
            (x+1, y)

            Un movimiento será inválido si pretende salir fuera del mapa o acceder a una celda con obstáculo.
            Si se realiza este movimiento, el mundo entraría en un estado inválido, y el agente debería morir.
            El agente dispondrá de cuatro sensores, uno por cada dirección cardinal (arriba, abajo, izquierda
            y derecha).
            Cada sensor informará de si la celda adyacente inmediata en esa dirección está libre o bloqueada.
            La percepción total del agente incluirá también su posición actual en el mapa y su nivel de energía
            consumida (1 unidad por cada movimiento realizado).
            La implementación deberá respetar una serie de principios arquitectónicos fundamentales.
            En primer lugar, el agente no puede acceder de forma directa al mundo, ni puede modificarlo, ni
            mucho menos mover su posición en él por sí mismo. Toda interacción con el mundo debe
            realizarse exclusivamente a través del entorno, que actuará como mediador.
            Esto implica que el agente no puede "ver" el mapa completo ni manipular las celdas directamente:
            su conocimiento del entorno es parcial y se limita a lo que perciben sus sensores. De igual forma,
            cuando el agente decide una acción, no la ejecuta él mismo; se limita a comunicar su decisión al
            entorno, que será el único responsable de verificar si dicha acción es válida y, en caso de serlo,
            aplicarla sobre el mundo.
            Además, cada acción debe suponer un consumo de energía por parte del agente.
            El ciclo de vida del sistema debe seguir de forma estricta la secuencia percepción → decisión →
            acción → validación, garantizando que el entorno controle en todo momento la coherencia del
            estado del mundo.
            -2-

            Finalmente, el diseño debe mantener una separación clara entre los distintos componentes:
            mundo, entorno, agente, sensores, percepciones, acciones y política de decisión.
            La implementación de estos componentes y su correcta interacción constituirán un criterio de
            evaluación esencial de la práctica.
            El propósito de esta práctica no se limita a lograr que el agente se desplace correctamente por el
            mapa, sino a diseñar un modelo bien estructurado que refleje una separación real entre el agente
            y el entorno.
            El agente debe comportarse como una entidad autónoma e independiente, capaz de tomar
            decisiones basadas en información incompleta, mientras que el entorno será el encargado de
            mantener la integridad del mundo y de controlar todas las acciones que lo afectan.

            3 Ejercicios a resolver
            Como se comentó al principio, el agente tendrá que ser capaz de dirigirse hacia su objetivo de la
            forma más eficiente posible, evitando obstáculos, y por supuesto, evitando salirse de los márgenes
            de su mundo.
            En particular, el agente deberá, como mínimo, poder resolver los siguientes mundos o mapas:
            Sin obstáculos, yendo directo al objetivo.
            Con obstáculos básicos. Una línea horizontal de obstáculos, así como una línea vertical
            Con obstáculos cóncavos. El agente tendrá que ser capaz de enfrentarse a obstáculos en
            forma de U invertida.
            Con obstáculos convexos. El agente tendrá que ser capaz de enfrentarse a obstáculos en
            forma de U.

            4 Evaluación de la práctica
            La práctica se evaluará sobre 10 puntos, considerando los siguientes apartados:
            Correcta resolución de los mundos. Se tendrá en cuenta la energía consumida y se
            comparará con la obtenida por el resto de grupos. (NOTA se penalizará agentes que
            consuman demasiada energía en llegar a su objetivo)
            o Mundo sin obstáculos y mundo con obstáculos básicos (0.5 puntos).
            o Mundo con obstáculos cóncavos (0.5 puntos).
            o Mundo con obstáculos convexos (1 punto).
            o Mundos complejos (2)
            o Correcta resolución de un mundo sorpresa (2 puntos).
            Código y justificación de la arquitectura de software. Se valorará la separación de las
            clases, el respeto del principio de responsabilidad única, así como la legibilidad del
            código (3 puntos).
            Presentación. En clase de teoría se realizará una presentación de 5 minutos explicando
            las decisiones de diseño e implementación que se han tomado, así como los errores
            cometidos y cómo se han resuelto los mismos. La presentación se evaluará de forma
            colectiva (1 puntos).

            5 Documentos a entregar
            Para la evaluación de la práctica se deberá entregar el código fuente, una memoria explicando la
            estructura de clases, así como las decisiones de diseño e implementación tomadas, y la
            presentación o video que se usará el día de la presentación.
            """;

    @Test
    @DisplayName("La rubrica de evaluacion del guion de JADE sobrevive al recorte del documento y no queda sin cubrir")
    void jadeEvaluationCriteriaSurviveTruncationAndAreFullyCovered() {
        assertThat(DOCUMENTO_JADE.length()).isGreaterThan(6_000);
        project.setBriefing(DOCUMENTO_JADE);
        modelReplies("""
                {"resumen":{"duracion_semanas":4,"num_sprints":2,"criterios_detectados":[
                  {"criterio":"Mundo sin obstaculos y con obstaculos basicos","peso_o_importancia":"0.5 puntos",
                   "tareas_relacionadas":["Resolver mundo sin obstaculos y con obstaculos basicos"]},
                  {"criterio":"Mundo con obstaculos concavos","peso_o_importancia":"0.5 puntos",
                   "tareas_relacionadas":["Resolver mundo con obstaculos concavos"]},
                  {"criterio":"Mundo con obstaculos convexos","peso_o_importancia":"1 punto",
                   "tareas_relacionadas":["Resolver mundo con obstaculos convexos"]},
                  {"criterio":"Mundos complejos","peso_o_importancia":"2 puntos",
                   "tareas_relacionadas":["Resolver mundos complejos"]},
                  {"criterio":"Mundo sorpresa","peso_o_importancia":"2 puntos",
                   "tareas_relacionadas":["Resolver el mundo sorpresa"]},
                  {"criterio":"Arquitectura de software","peso_o_importancia":"3 puntos",
                   "tareas_relacionadas":["Disenar y justificar la arquitectura de software"]},
                  {"criterio":"Presentacion","peso_o_importancia":"1 punto",
                   "tareas_relacionadas":["Preparar y realizar la presentacion"]}
                ]},
                 "tareas":[
                  {"titulo":"Resolver mundo sin obstaculos y con obstaculos basicos","sprint":1,"asignado":"Ana Garcia","horas":4},
                  {"titulo":"Resolver mundo con obstaculos concavos","sprint":1,"asignado":"Ana Garcia","horas":4},
                  {"titulo":"Resolver mundo con obstaculos convexos","sprint":1,"asignado":"Luis Perez","horas":5},
                  {"titulo":"Resolver mundos complejos","sprint":2,"asignado":"Luis Perez","horas":6},
                  {"titulo":"Resolver el mundo sorpresa","sprint":2,"asignado":"Ana Garcia","horas":6},
                  {"titulo":"Disenar y justificar la arquitectura de software","sprint":1,"asignado":"Luis Perez","horas":5},
                  {"titulo":"Preparar y realizar la presentacion","sprint":2,"asignado":"Ana Garcia","horas":3}
                 ]}""");

        var response = service.generate(7L, null);

        // La rubrica empieza en el caracter 7137 del documento: por encima del
        // recorte de 6000. Si el prompt la lleva de todas formas es porque el
        // detector de criterios la ha sacado aparte, no porque haya sobrevivido
        // al recorte por su cuenta.
        assertThat(promptDeUsuario())
                .contains("CRITERIOS DE EVALUACION Y REQUISITOS DETECTADOS")
                .contains("0.5 puntos")
                .contains("Documentos a entregar");

        assertThat(response.tasks()).extracting(ProposedTask::title).contains(
                "Resolver mundo sin obstaculos y con obstaculos basicos",
                "Resolver mundo con obstaculos concavos",
                "Resolver mundo con obstaculos convexos",
                "Resolver mundos complejos",
                "Resolver el mundo sorpresa",
                "Disenar y justificar la arquitectura de software",
                "Preparar y realizar la presentacion");

        assertThat(response.warnings()).noneMatch(aviso -> aviso.contains("no tiene ninguna tarea asociada"));
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

    @Test
    @DisplayName("Las tareas marcadas PENDIENTE siguen saliendo como aviso, sin dejar de proponerse")
    void pendingTasksBecomeWarningsButStayInThePlan() {
        project.setDeadline(inicio.plusDays(13));
        modelReplies("""
                {"tareas":[{"titulo":"Escribir la historia de la funcionalidad X","sprint":1,
                  "asignado":"Ana Garcia","horas":4,
                  "motivo":"PENDIENTE: falta una historia de usuario concreta en el documento"}]}""");

        var response = service.generate(7L, null);

        assertThat(response.tasks()).hasSize(1);
        assertThat(response.warnings()).anySatisfy(aviso -> assertThat(aviso)
                .contains("Escribir la historia de la funcionalidad X")
                .contains("PENDIENTE: falta una historia de usuario concreta"));
    }

    @Test
    @DisplayName("Las fechas de entrega por iteracion del documento salen como aviso, sin tocar la planificacion")
    void iterationDeliveryDatesFromTheDocumentBecomeWarnings() {
        project.setBriefing("Curso 2025/2026\n1º Iteracion: 5-6 de\nretrospectiva.\nnoviembre.\n"
                + "* Se concretara en Prado la fecha exacta para cada grupo");
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.warnings()).anySatisfy(aviso -> assertThat(aviso)
                .contains("iteración 1")
                .contains("5 al 6 de noviembre de 2025")
                .contains("se confirmará aparte"));
        // El aviso no cambia ni las fechas de los sprints ni las de las tareas:
        // esas las sigue poniendo el servidor a partir del calendario, no del texto.
        assertThat(response.sprints().get(0).start()).isEqualTo(inicio);
    }

    @Test
    @DisplayName("Un documento muy largo se recorta antes de mandarlo al modelo, para no superar la cuota de Groq")
    void longDocumentsAreTruncatedBeforeReachingTheModel() {
        // Muy por debajo del tope de seguridad del extractor (80000): esto prueba
        // el recorte propio del prompt, que existe por la cuota de Groq (8000
        // tokens/minuto), no por el limite de contexto del modelo.
        String documentoLargo = "Apartado del guion. ".repeat(1000);
        project.setBriefing(documentoLargo);
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(promptDeUsuario())
                .contains("[...documento recortado por longitud...]")
                .doesNotContain(documentoLargo);
        assertThat(response.warnings()).anySatisfy(aviso -> assertThat(aviso)
                .contains("cuota de tokens por minuto de Groq"));
    }

    @Test
    @DisplayName("Un documento corto no lleva ningun aviso de recorte")
    void shortDocumentsAreNotTruncated() {
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(promptDeUsuario()).doesNotContain("recortado");
        assertThat(response.warnings()).noneMatch(aviso -> aviso.contains("cuota de tokens"));
    }

    // -----------------------------------------------------------------
    // JSON roto: reintentar y, si no hay manera, avisar de que no se pudo
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Un JSON roto se reintenta antes de darlo por perdido")
    void retriesOnceOnBrokenJson() {
        modelReplies("esto no es json", PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.attempts()).isEqualTo(2);
        assertThat(response.tasks()).hasSize(4);
    }

    @Test
    @DisplayName("Si nunca llega un plan que se pueda leer, se avisa de que la IA no ha respondido bien")
    void failsWhenNoAttemptIsReadable() {
        modelReplies("nada", "tampoco");

        assertThatThrownBy(() -> service.generate(7L, null))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("tras 2 intentos");
    }

    @Test
    @DisplayName("Los reintentos se pueden configurar")
    void retriesAreConfigurable() {
        properties.setMaxRetries(0);
        modelReplies("esto no es json");

        assertThatThrownBy(() -> service.generate(7L, null))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("tras 1 intentos");
    }

    @Test
    @DisplayName("Si Groq limita por cuota, se espera lo que dice y se reintenta, sin fallar a la primera")
    void waitsAndRetriesOnRateLimit() {
        when(groqClient.chatJson(eq(AiPlanService.SYSTEM_PROMPT), anyString()))
                .thenThrow(new GroqClient.RateLimitedException("Groq ha limitado la peticion", 0.01))
                .thenReturn(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.attempts()).isEqualTo(2);
        assertThat(response.tasks()).hasSize(4);
    }

    @Test
    @DisplayName("Si la cuota sigue llena en el ultimo intento, se propaga el aviso de Groq, no uno generico")
    void propagatesRateLimitOnTheLastAttempt() {
        properties.setMaxRetries(0);
        when(groqClient.chatJson(eq(AiPlanService.SYSTEM_PROMPT), anyString()))
                .thenThrow(new GroqClient.RateLimitedException("Groq ha limitado la peticion por su cuota", 0.01));

        assertThatThrownBy(() -> service.generate(7L, null))
                .isInstanceOf(GroqClient.RateLimitedException.class)
                .hasMessageContaining("cuota");
    }

    // -----------------------------------------------------------------
    // Fecha de inicio: formulario, workspace, documento o hoy
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Sin fecha en ningun sitio, el plan empieza hoy")
    void startDateFallsBackToToday() {
        project.setStartDate(null);
        project.setDeadline(null);
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.sprints().get(0).start()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Sin fecha en el workspace, se usa la marcada en el documento")
    void startDateComesFromTheDocument() {
        project.setStartDate(null);
        project.setDeadline(null);
        project.setBriefing("Fecha de inicio: 05/10/2026. Implementar una API REST de biblioteca con JWT.");
        modelReplies(PLAN_VALIDO);

        var response = service.generate(7L, null);

        assertThat(response.sprints().get(0).start()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    @DisplayName("La fecha del formulario manda sobre la del workspace, y la entrega tambien")
    void formDatesWinOverTheWorkspace() {
        modelReplies(PLAN_VALIDO);
        LocalDate otroInicio = inicio.plusWeeks(2);

        var response = service.generate(7L, new PlanOptions(otroInicio, otroInicio.plusDays(13), null, null));

        assertThat(response.sprints()).singleElement().satisfies(sprint -> {
            assertThat(sprint.start()).isEqualTo(otroInicio);
            assertThat(sprint.end()).isEqualTo(otroInicio.plusDays(13));
        });
    }

    @Test
    @DisplayName("La fecha se encuentra aunque este muy adelante en un documento largo, no solo al principio")
    void startDateIsFoundEvenFarIntoALongDocument() {
        project.setStartDate(null);
        project.setDeadline(null);
        // Mas de 12000 caracteres antes de la fecha: el limite que tenia el
        // extractor de documentos, que la habria cortado antes de que nadie
        // llegara a leerla.
        String relleno = "Este apartado describe el contexto del proyecto. ".repeat(400);
        String documento = relleno + "Fecha de inicio: 05/10/2026. " + relleno;
        var file = new MockMultipartFile("file", "guion-largo.txt", "text/plain",
                documento.getBytes(StandardCharsets.UTF_8));
        modelReplies(PLAN_VALIDO);

        var response = service.generateFromDocument(7L, file, null);

        assertThat(response.sprints().get(0).start()).isEqualTo(LocalDate.of(2026, 10, 5));
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
        verifyNoInteractions(groqClient);
    }

    @Test
    @DisplayName("Una descripcion escrita vacia se rechaza antes de llamar al modelo")
    void requiresADescription() {
        assertThatThrownBy(() -> service.generateFromText(7L, "   ", null))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(groqClient);
    }

    @Test
    @DisplayName("Con el modulo desactivado se avisa sin llamar al modelo")
    void refusesWhenDisabled() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.generate(7L, null))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("desactivado");
        verifyNoInteractions(groqClient);
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
                        "API", 1, TaskPriority.ALTA, BigDecimal.valueOf(6), fecha,
                        List.of(2L), List.of("Luis Perez"), List.of()))));

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
        assertThat(created.getAssignees()).extracting(User::getId).containsExactly(2L);
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
                new ProposedTask("Tarea", null, null, null, null, null, null, null, null,
                        List.of(99L), List.of("Nadie"), null))));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TaskType.TAREA);
        assertThat(captor.getValue().getAssignees()).isEmpty();
        assertThat(captor.getValue().getEpic()).isNull();
    }

    @Test
    @DisplayName("Con dos ids, uno ajeno al equipo, solo el valido entra en la tarea")
    void applyKeepsOnlyTheValidAssigneeAmongSeveral() {
        Board board = Board.builder().id(3L).project(project).build();
        board.getColumns().add(BoardColumn.builder().id(10L).name("Por hacer").position(0).board(board).build());
        when(boardService.getBoard(3L)).thenReturn(board);
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).fullName("Luis Perez").build()));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.apply(7L, new ApplyPlanRequest(3L, List.of(
                new ProposedTask("Tarea", null, null, null, null, null, null, null, null,
                        List.of(2L, 99L), List.of("Luis Perez", "Nadie"), null))));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignees()).extracting(User::getId).containsExactly(2L);
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
