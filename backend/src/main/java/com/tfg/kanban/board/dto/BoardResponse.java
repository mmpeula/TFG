package com.tfg.kanban.board.dto;

import com.tfg.kanban.domain.model.Board;
import com.tfg.kanban.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Tablero Kanban", example = """
        {"id":3,"name":"Tablero principal","description":"Seguimiento",
         "projectId":5,"projectName":"Practica 2","myRole":"MIEMBRO",
         "columns":[],"createdAt":"2026-08-21T10:15:30Z"}
        """)
public record BoardResponse(
        Long id,
        String name,
        String description,
        Long projectId,
        String projectName,
        @Schema(description = "Rol del usuario autenticado en el equipo dueno del tablero") Role myRole,
        @Schema(description = "Columnas del tablero; con sus tareas en el detalle") List<ColumnResponse> columns,
        Instant createdAt
) {

    public static BoardResponse summary(Board board, Role myRole) {
        return new BoardResponse(board.getId(), board.getName(), board.getDescription(),
                board.getProject().getId(), board.getProject().getName(), myRole, null, board.getCreatedAt());
    }

    public static BoardResponse detail(Board board, Role myRole, List<ColumnResponse> columns) {
        return new BoardResponse(board.getId(), board.getName(), board.getDescription(),
                board.getProject().getId(), board.getProject().getName(), myRole, columns, board.getCreatedAt());
    }
}
