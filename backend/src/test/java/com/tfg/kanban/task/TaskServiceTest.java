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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** Tests unitarios de la logica de tablero: limite WIP, reordenacion y asignaciones. */
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
    @DisplayName("Crear una tarea en una columna con el limite WIP lleno falla")
    void createFailsWhenWipLimitReached() {
        doing.setWipLimit(2);
        when(boardService.getColumn(11L)).thenReturn(doing);
        when(taskRepository.countByBoardColumn_Id(11L)).thenReturn(2L);

        TaskRequest request = new TaskRequest("Nueva tarea", null, null, null, null, null, null);

        assertThatThrownBy(() -> taskService.create(11L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite WIP");
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
        assertThat(first.getPosition()).isZero();
        assertThat(second.getPosition()).isEqualTo(2);
        // La columna de origen se reindexa: la tarea que queda pasa a la posicion 0
        assertThat(stays.getPosition()).isZero();
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

        TaskRequest request = new TaskRequest("Tarea", null, null, TaskPriority.ALTA, null, null, 99L);

        assertThatThrownBy(() -> taskService.update(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no pertenece al equipo");
    }

    @Test
    @DisplayName("Reordenar dentro de la misma columna no comprueba el limite WIP")
    void reorderInsideSameColumnIgnoresWipLimit() {
        todo.setWipLimit(2);
        Task moved = task(1L, "Tarea 1", todo, 0);
        Task other = task(2L, "Tarea 2", todo, 1);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(moved));
        when(boardService.getColumn(10L)).thenReturn(todo);
        when(taskRepository.findByBoardColumn_IdOrderByPositionAsc(10L)).thenReturn(List.of(moved, other));
        when(taskMapper.toResponse(any())).thenReturn(null);
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        taskService.move(1L, new MoveTaskRequest(10L, 1));

        assertThat(moved.getPosition()).isEqualTo(1);
        assertThat(other.getPosition()).isZero();
    }
}
