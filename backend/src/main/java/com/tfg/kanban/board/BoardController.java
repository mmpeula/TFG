package com.tfg.kanban.board;

import com.tfg.kanban.board.dto.BoardRequest;
import com.tfg.kanban.board.dto.BoardResponse;
import com.tfg.kanban.board.dto.ColumnRequest;
import com.tfg.kanban.board.dto.ColumnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Tableros", description = "Tableros Kanban y sus columnas")
public class BoardController {

    private final BoardService boardService;

    @Operation(summary = "Tableros de un proyecto")
    @PreAuthorize("@access.canReadProject(#projectId)")
    @GetMapping("/api/projects/{projectId}/boards")
    public List<BoardResponse> findByProject(@PathVariable Long projectId) {
        return boardService.findByProject(projectId);
    }

    @Operation(summary = "Crear un tablero",
            description = "Solo el RESPONSABLE. El tablero nace con las columnas 'Por hacer', 'En progreso' y 'Hecho'.")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Tablero creado"))
    @PreAuthorize("@access.canManageProject(#projectId)")
    @PostMapping("/api/projects/{projectId}/boards")
    public ResponseEntity<BoardResponse> create(@PathVariable Long projectId,
                                                @Valid @RequestBody BoardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boardService.create(projectId, request));
    }

    @Operation(summary = "Detalle del tablero",
            description = "Devuelve las columnas ordenadas con todas sus tareas. Es la llamada que usa el Kanban.")
    @PreAuthorize("@access.canReadBoard(#id)")
    @GetMapping("/api/boards/{id}")
    public BoardResponse findDetail(@PathVariable Long id) {
        return boardService.findDetail(id);
    }

    @Operation(summary = "Editar un tablero", description = "Solo el RESPONSABLE.")
    @PreAuthorize("@access.canManageBoard(#id)")
    @PutMapping("/api/boards/{id}")
    public BoardResponse update(@PathVariable Long id, @Valid @RequestBody BoardRequest request) {
        return boardService.update(id, request);
    }

    @Operation(summary = "Borrar un tablero", description = "Solo el RESPONSABLE. Arrastra columnas y tareas.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Tablero borrado"))
    @PreAuthorize("@access.canManageBoard(#id)")
    @DeleteMapping("/api/boards/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boardService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Columnas de un tablero")
    @PreAuthorize("@access.canReadBoard(#boardId)")
    @GetMapping("/api/boards/{boardId}/columns")
    public List<ColumnResponse> findColumns(@PathVariable Long boardId) {
        return boardService.findColumns(boardId);
    }

    @Operation(summary = "Crear una columna", description = "Solo el RESPONSABLE. Se anade al final del tablero.")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Columna creada"))
    @PreAuthorize("@access.canManageBoard(#boardId)")
    @PostMapping("/api/boards/{boardId}/columns")
    public ResponseEntity<ColumnResponse> createColumn(@PathVariable Long boardId,
                                                       @Valid @RequestBody ColumnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boardService.createColumn(boardId, request));
    }
}
