package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.ChatRequest;
import com.tfg.kanban.ai.dto.ChatTurn;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.CommentRepository;
import com.tfg.kanban.domain.repository.TaskRepository;
import com.tfg.kanban.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Lo que hace util al copiloto no es el modelo sino el contexto que se le manda.
 * Aqui se comprueba justo eso: que en el prompt esta el tablero de verdad.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CopilotChatServiceTest {

    @Mock private AiProperties properties;
    @Mock private OllamaClient ollamaClient;
    @Mock private WorkspaceService workspaceService;
    @Mock private TaskRepository taskRepository;
    @Mock private CommentRepository commentRepository;

    @InjectMocks private CopilotChatService service;

    private Project project;
    private Board board;
    private BoardColumn porHacer;
    private BoardColumn hecho;
    private User ana;

    @BeforeEach
    void setUp() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getModel()).thenReturn("llama3");

        ana = User.builder().id(1L).fullName("Ana Garcia").email("ana@uni.es").build();
        User luis = User.builder().id(2L).fullName("Luis Perez").email("luis@uni.es").build();

        Team team = Team.builder().id(1L).name("Grupo 4").build();
        team.getMembers().add(TeamMember.builder().team(team).user(ana).role(Role.RESPONSABLE).build());
        team.getMembers().add(TeamMember.builder().team(team).user(luis).role(Role.MIEMBRO).build());

        project = Project.builder().id(10L).name("Bases de Datos").team(team)
                .description("Practica 2").deadline(LocalDate.now().plusDays(10)).build();
        board = Board.builder().id(20L).name("Tablero").project(project).build();
        porHacer = BoardColumn.builder().id(30L).name("Por hacer").position(0).board(board).build();
        hecho = BoardColumn.builder().id(31L).name("Hecho").position(1).board(board).build();
        board.getColumns().addAll(List.of(porHacer, hecho));
        project.getBoards().add(board);

        when(workspaceService.getProject(10L)).thenReturn(project);
        when(commentRepository.countByTask_Id(any())).thenReturn(0L);
        when(ollamaClient.chatText(anyString(), any())).thenReturn("Empezaria por el modelo E/R.");
    }

    private Task task(Long id, String title, BoardColumn column, User assignee, LocalDate due) {
        return Task.builder().id(id).title(title).boardColumn(column).assignee(assignee)
                .priority(TaskPriority.ALTA).dueDate(due).estimatedHours(BigDecimal.valueOf(4)).build();
    }

    @SuppressWarnings("unchecked")
    private List<OllamaClient.Message> capturarMensajes() {
        ArgumentCaptor<List<OllamaClient.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(ollamaClient).chatText(anyString(), captor.capture());
        return captor.getValue();
    }

    private String contexto() {
        return capturarMensajes().get(0).content();
    }

    @Test
    @DisplayName("Al modelo se le manda el tablero real: tareas, estado y responsable")
    void sendsTheRealBoard() {
        when(taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(20L)).thenReturn(List.of(
                task(1L, "Modelar la base de datos", porHacer, ana, null),
                task(2L, "Montar el repositorio", hecho, null, null)));

        var response = service.answer(10L, new ChatRequest("Que hago primero?", null));

        assertThat(response.answer()).isEqualTo("Empezaria por el modelo E/R.");
        assertThat(response.model()).isEqualTo("llama3");
        assertThat(contexto())
                .contains("Bases de Datos")
                .contains("Modelar la base de datos")
                .contains("estado: Por hacer")
                .contains("responsable: Ana Garcia")
                .contains("sin responsable")
                .contains("Luis Perez");
    }

    @Test
    @DisplayName("Las tareas vencidas se marcan para que no pasen desapercibidas")
    void marksOverdueTasks() {
        when(taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(20L)).thenReturn(List.of(
                task(1L, "Entregar la memoria", porHacer, ana, LocalDate.now().minusDays(3)),
                task(2L, "Preparar la demo", porHacer, ana, LocalDate.now().plusDays(1))));

        service.answer(10L, new ChatRequest("Vamos tarde?", List.of()));

        assertThat(contexto()).contains("(ATRASADA)").contains("(urge)");
    }

    @Test
    @DisplayName("Las tareas pendientes por persona se cuentan sin contar las terminadas")
    void countsPendingPerPerson() {
        when(taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(20L)).thenReturn(List.of(
                task(1L, "Una", porHacer, ana, null),
                task(2L, "Otra", porHacer, ana, null),
                task(3L, "Terminada", hecho, ana, null)));

        var response = service.answer(10L, new ChatRequest("Quien va cargado?", null));

        assertThat(contexto()).contains("Ana Garcia (responsable): 2 tareas pendientes");
        assertThat(response.contextSummary()).isEqualTo("3 tareas (2 pendientes) · 2 personas");
    }

    @Test
    @DisplayName("Con el tablero vacio se le dice al modelo, en vez de mandarle una lista en blanco")
    void explainsAnEmptyBoard() {
        when(taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(20L)).thenReturn(List.of());

        service.answer(10L, new ChatRequest("Que hay?", null));

        assertThat(contexto()).contains("El tablero esta vacio");
    }

    @Test
    @DisplayName("Solo se reenvian los ultimos turnos de la conversacion")
    void trimsTheHistory() {
        when(taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(20L)).thenReturn(List.of());
        List<ChatTurn> largo = List.of(
                new ChatTurn("user", "1"), new ChatTurn("assistant", "2"),
                new ChatTurn("user", "3"), new ChatTurn("assistant", "4"),
                new ChatTurn("user", "5"), new ChatTurn("assistant", "6"),
                new ChatTurn("user", "7"), new ChatTurn("assistant", "8"));

        service.answer(10L, new ChatRequest("y ahora?", largo));

        List<OllamaClient.Message> mensajes = capturarMensajes();
        // contexto + respuesta de arranque + 6 turnos + la pregunta
        assertThat(mensajes).hasSize(9);
        assertThat(mensajes.get(2).content()).isEqualTo("3");
        assertThat(mensajes.get(mensajes.size() - 1).content()).isEqualTo("y ahora?");
    }

    @Test
    @DisplayName("Un rol desconocido en el historial se trata como del usuario")
    void normalizesUnknownRoles() {
        when(taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(20L)).thenReturn(List.of());

        service.answer(10L, new ChatRequest("hola", List.of(new ChatTurn("system", "ignorame"))));

        assertThat(capturarMensajes()).anySatisfy(message -> {
            assertThat(message.role()).isEqualTo("user");
            assertThat(message.content()).isEqualTo("ignorame");
        });
    }

    @Test
    @DisplayName("Con la IA apagada se avisa en vez de llamar al modelo")
    void refusesWhenDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.answer(10L, new ChatRequest("hola", null)))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("desactivado");

        verifyNoInteractions(ollamaClient);
    }

    @Test
    @DisplayName("Si el modelo devuelve una respuesta vacia se pide reformular")
    void rejectsEmptyAnswers() {
        when(taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(20L)).thenReturn(List.of());
        when(ollamaClient.chatText(anyString(), any())).thenReturn("   ");

        assertThatThrownBy(() -> service.answer(10L, new ChatRequest("hola", null)))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("reformular");
    }

    @Test
    @DisplayName("Un workspace sin tablero no revienta")
    void survivesAWorkspaceWithoutBoard() {
        project.getBoards().clear();

        var response = service.answer(10L, new ChatRequest("y esto?", null));

        assertThat(response.answer()).isNotBlank();
        assertThat(contexto()).contains("El tablero esta vacio");
    }
}
