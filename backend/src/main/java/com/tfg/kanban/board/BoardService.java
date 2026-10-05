package com.tfg.kanban.board;

import com.tfg.kanban.board.dto.*;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.*;
import com.tfg.kanban.domain.repository.BoardColumnRepository;
import com.tfg.kanban.domain.repository.BoardRepository;
import com.tfg.kanban.domain.repository.TaskRepository;
import com.tfg.kanban.domain.repository.TeamMemberRepository;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.task.TaskMapper;
import com.tfg.kanban.task.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * El tablero de cada workspace y sus columnas. El tablero nace con su workspace
 * (ver {@link DefaultBoard}) con tres columnas fijas —Por hacer, En progreso y
 * Hecho— que no se crean, editan ni borran: aqui solo se leen. Todo lo demas
 * (el plan, las senales, «hecha» = ultima columna) cuenta con esas tres.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final TeamMemberRepository teamMemberRepository;
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

    public Board getBoard(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Tablero", boardId));
    }

    public BoardColumn getColumn(Long columnId) {
        return columnRepository.findById(columnId)
                .orElseThrow(() -> new ResourceNotFoundException("Columna", columnId));
    }

    private Role roleInBoard(Board board) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> teamMemberRepository.findRoleByProject(board.getProject().getId(), userId))
                .orElse(null);
    }
}
