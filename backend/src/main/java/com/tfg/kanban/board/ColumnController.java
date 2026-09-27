package com.tfg.kanban.board;

import com.tfg.kanban.board.dto.ColumnRequest;
import com.tfg.kanban.board.dto.ColumnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/columns")
@RequiredArgsConstructor
@Tag(name = "Columnas", description = "Columnas de un tablero Kanban")
public class ColumnController {

    private final BoardService boardService;

    @Operation(summary = "Editar una columna",
            description = "Solo el RESPONSABLE. No se puede fijar un limite WIP menor que las tareas que ya tiene.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Columna actualizada"),
            @ApiResponse(responseCode = "409", description = "El limite WIP es menor que el numero de tareas")
    })
    @PreAuthorize("@access.canManageColumn(#id)")
    @PutMapping("/{id}")
    public ColumnResponse update(@PathVariable Long id, @Valid @RequestBody ColumnRequest request) {
        return boardService.updateColumn(id, request);
    }

    @Operation(summary = "Borrar una columna",
            description = "Solo el RESPONSABLE, y solo si esta vacia y no es la ultima del tablero.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Columna borrada"),
            @ApiResponse(responseCode = "409", description = "La columna tiene tareas o es la ultima")
    })
    @PreAuthorize("@access.canManageColumn(#id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boardService.deleteColumn(id);
        return ResponseEntity.noContent().build();
    }
}
