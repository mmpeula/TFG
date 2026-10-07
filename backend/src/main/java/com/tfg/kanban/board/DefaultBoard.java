package com.tfg.kanban.board;

import com.tfg.kanban.domain.model.Board;
import com.tfg.kanban.domain.model.BoardColumn;
import com.tfg.kanban.domain.model.Project;

import java.util.List;

/**
 * Con que columnas nace un tablero.
 *
 * <p>Solo se crea uno por workspace, al crearlo, y sus tres columnas son fijas:
 * todo lo demas (el progreso del workspace, el plan, los avisos) lee la primera
 * como «por hacer» y la ultima como «hecho».</p>
 */
public final class DefaultBoard {

    /** Las tres columnas clasicas de un Kanban, en orden. */
    private static final List<String> COLUMN_NAMES = List.of("Por hacer", "En progreso", "Hecho");

    private DefaultBoard() {
    }

    /** Un tablero del proyecto con sus columnas por defecto, todavia sin guardar. */
    public static Board of(String name, String description, Project project) {
        Board board = Board.builder()
                .name(name)
                .description(description)
                .project(project)
                .build();

        for (int position = 0; position < COLUMN_NAMES.size(); position++) {
            board.getColumns().add(BoardColumn.builder()
                    .board(board)
                    .name(COLUMN_NAMES.get(position))
                    .position(position)
                    .build());
        }
        return board;
    }
}
