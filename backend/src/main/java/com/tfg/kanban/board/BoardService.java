package com.tfg.kanban.board;

import com.tfg.kanban.activity.ActivityService;
import com.tfg.kanban.board.dto.*;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.BoardColumnRepository;
import com.tfg.kanban.domain.repository.BoardRepository;
import com.tfg.kanban.domain.repository.TaskRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.project.ProjectService;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.task.TaskMapper;
import com.tfg.kanban.task.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tableros y sus columnas.
 *
 * Al crear un tablero se generan las tres columnas clasicas de un Kanban
 * ("Por hacer", "En progreso", "Hecho") para que sea usable desde el primer momento.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private static final List<String> DEFAULT_COLUMNS = List.of("Por hacer", "En progreso", "Hecho");

    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectService projectService;
    private final ActivityService activityService;
    private final TaskMapper taskMapper;

    public List<BoardResponse> findByProject(Long projectId) {
        return boardRepository.findByProject_Id(projectId).stream()
                .map(board -> BoardResponse.summary(board, roleInBoard(board)))
                .toList();
    }

    /** Tablero completo: columnas ordenadas con sus tareas. Es lo que pinta el Kanban. */
    public BoardResponse findDetail(Long boardId) {
        Board board = getBoard(boardId);

        List<BoardColumn> columns = columnRepository.findByBoard_IdOrderByPositionAsc(boardId);
        List<Task> tasks = taskRepository.findByBoardColumn_Board_IdOrderByPositionAsc(boardId);
        Map<Long, List<TaskResponse>> tasksByColumn = taskMapper.toResponses(tasks).stream()
                .collect(Collectors.groupingBy(TaskResponse::columnId));

        List<ColumnResponse> columnResponses = columns.stream()
                .map(column -> ColumnResponse.withTasks(column,
                        tasksByColumn.getOrDefault(column.getId(), List.of())))
                .toList();

        return BoardResponse.detail(board, roleInBoard(board), columnResponses);
    }

    @Transactional
    public BoardResponse create(Long projectId, BoardRequest request) {
        Project project = projectService.getProject(projectId);

        Board board = Board.builder()
                .name(request.name().trim())
                .description(request.description())
                .project(project)
                .build();

        for (int i = 0; i < DEFAULT_COLUMNS.size(); i++) {
            board.getColumns().add(BoardColumn.builder()
                    .board(board)
                    .name(DEFAULT_COLUMNS.get(i))
                    .position(i)
                    .build());
        }

        Board saved = boardRepository.save(board);
        activityService.record(project, ActivityAction.CREACION, "Board", saved.getId(),
                "Creo el tablero '%s'".formatted(saved.getName()));

        return BoardResponse.summary(saved, roleInBoard(saved));
    }

    @Transactional
    public BoardResponse update(Long boardId, BoardRequest request) {
        Board board = getBoard(boardId);
        board.setName(request.name().trim());
        board.setDescription(request.description());

        activityService.record(board.getProject(), ActivityAction.ACTUALIZACION, "Board", board.getId(),
                "Actualizo el tablero '%s'".formatted(board.getName()));

        return BoardResponse.summary(board, roleInBoard(board));
    }

    @Transactional
    public void delete(Long boardId) {
        Board board = getBoard(boardId);
        activityService.record(board.getProject(), ActivityAction.BORRADO, "Board", board.getId(),
                "Borro el tablero '%s'".formatted(board.getName()));
        boardRepository.delete(board);
    }

    public List<ColumnResponse> findColumns(Long boardId) {
        getBoard(boardId);
        return columnRepository.findByBoard_IdOrderByPositionAsc(boardId).stream()
                .map(column -> ColumnResponse.summary(column, (int) taskRepository.countByBoardColumn_Id(column.getId())))
                .toList();
    }

    @Transactional
    public ColumnResponse createColumn(Long boardId, ColumnRequest request) {
        Board board = getBoard(boardId);
        int position = (int) columnRepository.countByBoard_Id(boardId);

        BoardColumn column = columnRepository.save(BoardColumn.builder()
                .board(board)
                .name(request.name().trim())
                .wipLimit(request.wipLimit())
                .position(position)
                .build());

        activityService.record(board.getProject(), ActivityAction.CREACION, "BoardColumn", column.getId(),
                "Creo la columna '%s'".formatted(column.getName()));

        return ColumnResponse.summary(column, 0);
    }

    @Transactional
    public ColumnResponse updateColumn(Long columnId, ColumnRequest request) {
        BoardColumn column = getColumn(columnId);
        long tasks = taskRepository.countByBoardColumn_Id(columnId);

        if (request.wipLimit() != null && tasks > request.wipLimit()) {
            throw new BusinessRuleException(
                    "La columna tiene %d tareas, mas que el limite WIP solicitado (%d)"
                            .formatted(tasks, request.wipLimit()));
        }

        column.setName(request.name().trim());
        column.setWipLimit(request.wipLimit());

        activityService.record(column.getBoard().getProject(), ActivityAction.ACTUALIZACION,
                "BoardColumn", column.getId(), "Actualizo la columna '%s'".formatted(column.getName()));

        return ColumnResponse.summary(column, (int) tasks);
    }

    /** Reordena una columna dentro del tablero (arrastrar y soltar cabeceras). */
    @Transactional
    public List<ColumnResponse> moveColumn(Long columnId, MovePositionRequest request) {
        BoardColumn column = getColumn(columnId);
        Long boardId = column.getBoard().getId();

        List<BoardColumn> columns = new ArrayList<>(columnRepository.findByBoard_IdOrderByPositionAsc(boardId));
        columns.removeIf(candidate -> candidate.getId().equals(columnId));

        int target = Math.min(Math.max(request.position(), 0), columns.size());
        columns.add(target, column);
        reindex(columns);

        activityService.record(column.getBoard().getProject(), ActivityAction.MOVIMIENTO,
                "BoardColumn", column.getId(),
                "Movio la columna '%s' a la posicion %d".formatted(column.getName(), target));

        return columns.stream()
                .map(item -> ColumnResponse.summary(item, (int) taskRepository.countByBoardColumn_Id(item.getId())))
                .toList();
    }

    @Transactional
    public void deleteColumn(Long columnId) {
        BoardColumn column = getColumn(columnId);
        Long boardId = column.getBoard().getId();

        if (taskRepository.countByBoardColumn_Id(columnId) > 0) {
            throw new BusinessRuleException(
                    "La columna tiene tareas; muevelas o borralas antes de eliminar la columna");
        }
        if (columnRepository.countByBoard_Id(boardId) <= 1) {
            throw new BusinessRuleException("El tablero debe tener al menos una columna");
        }

        activityService.record(column.getBoard().getProject(), ActivityAction.BORRADO,
                "BoardColumn", column.getId(), "Borro la columna '%s'".formatted(column.getName()));

        columnRepository.delete(column);
        reindex(columnRepository.findByBoard_IdOrderByPositionAsc(boardId).stream()
                .filter(item -> !item.getId().equals(columnId))
                .toList());
    }

    public Board getBoard(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Tablero", boardId));
    }

    public BoardColumn getColumn(Long columnId) {
        return columnRepository.findById(columnId)
                .orElseThrow(() -> new ResourceNotFoundException("Columna", columnId));
    }

    private void reindex(List<BoardColumn> columns) {
        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).setPosition(i);
        }
    }

    private Role roleInBoard(Board board) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> teamMemberRepository.findRoleByProject(board.getProject().getId(), userId))
                .orElse(null);
    }
}
