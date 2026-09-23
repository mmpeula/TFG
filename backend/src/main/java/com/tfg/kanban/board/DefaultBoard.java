package com.tfg.kanban.board;

import com.tfg.kanban.domain.model.Board;
import com.tfg.kanban.domain.model.BoardColumn;
import com.tfg.kanban.domain.model.Project;

import java.util.List;

/**
 * Con que columnas nace un tablero.
 *
 * <p>Vive aparte porque hay dos caminos que crean tableros —el workspace nuevo,
 * que trae el suyo hecho, y anadir otro tablero a un proyecto que ya existe— y
 * los dos tienen que dar exactamente el mismo resultado. Separados, bastaba con
 * tocar uno para que un tablero naciera con columnas distintas segun por donde
 * se hubiera creado, y el progreso del workspace (que lee la ultima columna
 * como «hecho») dejaba de significar lo mismo en los dos casos.</p>
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
