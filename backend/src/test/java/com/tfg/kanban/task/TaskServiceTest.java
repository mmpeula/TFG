package com.tfg.kanban.task;

import com.tfg.kanban.TestSecurity;
import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.board.BoardService;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.TaskRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.task.dto.MoveTaskRequest;
import com.tfg.kanban.task.dto.TaskRequest;
import org.junit.jupiter.api.AfterEach;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitarios de la logica de tablero: reordenacion y asignaciones. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private BoardService boardService;
    @Mock
    private ActivityService activityService;
    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private TaskService taskService;

    private Team team;
    private Project project;
    private Board board;
    private BoardColumn todo;
    private BoardColumn doing;

    @BeforeEach
    void setUp() {
        TestSecurity.authenticateAs(1L, "MIEMBRO");

        team = Team.builder().id(1L).name("Equipo").build();
        project = Project.builder().id(1L).name("Proyecto").team(team).build();
        board = Board.builder().id(1L).name("Tablero").project(project).build();
        todo = BoardColumn.builder().id(10L).name("Por hacer").position(0).board(board).build();
        doing = BoardColumn.builder().id(11L).name("En curso").position(1).board(board).build();
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    private Task task(Long id, String title, BoardColumn column, int position) {
        return Task.builder().id(id).title(title).boardColumn(column).position(position).build();
    }

    @Test
    @DisplayName("Mover una tarea a otra columna recoloca origen y destino sin huecos")
    void moveReindexesBothColumns() {
        Task moved = task(1L, "Tarea 1", todo, 0);
        Task stays = task(2L, "Tarea 2", todo, 1);
        Task first = task(3L, "Tarea 3", doing, 0);
        Task second = task(4L, "Tarea 4", doing, 1);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(moved));
        when(boardService.getColumn(11L)).thenReturn(doing);
        when(taskRepository.countByBoardColumn_Id(11L)).thenReturn(2L);
        when(taskRepository.findByBoardColumn_IdOrderByPositionAsc(11L))
                .thenReturn(List.of(first, second));
        when(taskRepository.findByBoardColumn_IdOrderByPositionAsc(10L))
                .thenReturn(List.of(moved, stays));

        taskService.move(1L, new MoveTaskRequest(11L, 1));

        assertThat(moved.getBoardColumn()).isEqualTo(doing);
        assertThat(moved.getPosition()).isEqualTo(1);
        // Las demas se recolocan en la base de datos, sin pasar por la entidad (asi no cambia
        // su ultima modificacion), y solo las que de verdad se desplazan.
        verify(taskRepository, never()).updatePosition(eq(3L), anyInt());
        verify(taskRepository).updatePosition(4L, 2);
        // La columna de origen se reindexa: la tarea que queda pasa a la posicion 0
        verify(taskRepository).updatePosition(2L, 0);
        verify(taskRepository, never()).updatePosition(eq(1L), anyInt());
    }

    @Test
    @DisplayName("No se puede mover una tarea a un tablero distinto")
    void cannotMoveToAnotherBoard() {
        Board other = Board.builder().id(2L).name("Otro tablero").project(project).build();
        BoardColumn foreign = BoardColumn.builder().id(20L).name("Ajena").board(other).build();
        Task moved = task(1L, "Tarea", todo, 0);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(moved));
        when(boardService.getColumn(20L)).thenReturn(foreign);

        assertThatThrownBy(() -> taskService.move(1L, new MoveTaskRequest(20L, 0)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tablero distinto");
    }

    @Test
    @DisplayName("Solo se puede asignar la tarea a alguien del equipo del proyecto")
    void cannotAssignUserOutsideTeam() {
        Task existing = task(1L, "Tarea", todo, 0);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(1L, 99L)).thenReturn(false);

        TaskRequest request = new TaskRequest("Tarea", null, null, TaskPriority.ALTA, null, null, null, List.of(99L));

        assertThatThrownBy(() -> taskService.update(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no pertenece al equipo");
    }

    @Test
    @DisplayName("Reordenar dentro de la misma columna recoloca las posiciones")
    void reorderInsideSameColumn() {
        Task moved = task(1L, "Tarea 1", todo, 0);
        Task other = task(2L, "Tarea 2", todo, 1);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(moved));
        when(boardService.getColumn(10L)).thenReturn(todo);
        when(taskRepository.findByBoardColumn_IdOrderByPositionAsc(10L)).thenReturn(List.of(moved, other));
        when(taskMapper.toResponse(any())).thenReturn(null);
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        taskService.move(1L, new MoveTaskRequest(10L, 1));

        assertThat(moved.getPosition()).isEqualTo(1);
        verify(taskRepository).updatePosition(2L, 0);
    }

    @Test
    @DisplayName("Al borrar una tarea, las de detras suben un puesto sin tocar su ultima modificacion")
    void deleteClosesTheGapWithoutTouchingTheOthers() {
        Task deleted = task(1L, "Tarea 1", todo, 0);
        Task next = task(2L, "Tarea 2", todo, 1);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(taskRepository.findByBoardColumn_IdOrderByPositionAsc(10L)).thenReturn(List.of(next));

        taskService.delete(1L);

        verify(taskRepository).delete(deleted);
        verify(taskRepository).updatePosition(2L, 0);
        assertThat(next.getPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("Se puede asignar una tarea a varias personas del equipo a la vez")
    void assignsMultipleUsersFromTheTeam() {
        User ana = User.builder().id(2L).fullName("Ana").build();
        User luis = User.builder().id(3L).fullName("Luis").build();
        when(boardService.getColumn(11L)).thenReturn(doing);
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(1L, 2L)).thenReturn(true);
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(1L, 3L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).fullName("Yo").build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(ana));
        when(userRepository.findById(3L)).thenReturn(Optional.of(luis));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TaskRequest request = new TaskRequest("Tarea", null, null, null, null, null, null, List.of(2L, 3L));
        taskService.create(11L, request);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignees()).extracting(User::getId).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("Los ids repetidos de la misma persona se deduplican")
    void deduplicatesRepeatedAssigneeIds() {
        User ana = User.builder().id(2L).fullName("Ana").build();
        when(boardService.getColumn(11L)).thenReturn(doing);
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(1L, 2L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).fullName("Yo").build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(ana));
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TaskRequest request = new TaskRequest("Tarea", null, null, null, null, null, null, List.of(2L, 2L));
        taskService.create(11L, request);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignees()).hasSize(1);
    }

    @Test
    @DisplayName("Si cualquiera de varios responsables no pertenece al equipo, falla entero")
    void cannotAssignAnyUserOutsideTeamEvenIfOthersAreValid() {
        Task existing = task(1L, "Tarea", todo, 0);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(1L, 2L)).thenReturn(true);
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(1L, 99L)).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).fullName("Ana").build()));

        TaskRequest request = new TaskRequest("Tarea", null, null, TaskPriority.ALTA, null, null, null, List.of(2L, 99L));

        assertThatThrownBy(() -> taskService.update(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no pertenece al equipo");
    }

    @Test
    @DisplayName("Pasar de un responsable a dos registra la asignacion")
    void changingFromOneToSeveralAssigneesLogsActivity() {
        User ana = User.builder().id(2L).fullName("Ana").build();
        User luis = User.builder().id(3L).fullName("Luis").build();
        Task existing = task(1L, "Tarea", todo, 0);
        existing.getAssignees().add(ana);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(1L, 2L)).thenReturn(true);
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(1L, 3L)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(ana));
        when(userRepository.findById(3L)).thenReturn(Optional.of(luis));

        TaskRequest request = new TaskRequest("Tarea", null, null, null, null, null, null, List.of(2L, 3L));
        taskService.update(1L, request);

        verify(activityService).record(eq(project), eq(ActivityAction.ASIGNACION), eq("Task"), eq(1L), anyString());
    }

    @Test
    @DisplayName("Vaciar los responsables tambien registra la asignacion")
    void removingAllAssigneesLogsActivity() {
        User ana = User.builder().id(2L).fullName("Ana").build();
        Task existing = task(1L, "Tarea", todo, 0);
        existing.getAssignees().add(ana);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));

        TaskRequest request = new TaskRequest("Tarea", null, null, null, null, null, null, List.of());
        taskService.update(1L, request);

        verify(activityService).record(eq(project), eq(ActivityAction.ASIGNACION), eq("Task"), eq(1L),
                contains("sin asignar"));
    }

    @Test
    @DisplayName("Si el conjunto de responsables no cambia, no se registra una nueva asignacion aunque llegue en otro orden")
    void unchangedAssigneeSetDoesNotLogActivity() {
        User ana = User.builder().id(2L).fullName("Ana").build();
        User luis = User.builder().id(3L).fullName("Luis").build();
        Task existing = task(1L, "Tarea", todo, 0);
        existing.getAssignees().addAll(List.of(ana, luis));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(1L, 2L)).thenReturn(true);
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(1L, 3L)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(ana));
        when(userRepository.findById(3L)).thenReturn(Optional.of(luis));

        // Mismo conjunto, pero en el orden inverso al que ya tenia.
        TaskRequest request = new TaskRequest("Tarea", null, null, null, null, null, null, List.of(3L, 2L));
        taskService.update(1L, request);

        verify(activityService, never()).record(any(), eq(ActivityAction.ASIGNACION), any(), any(), anyString());
    }
}
