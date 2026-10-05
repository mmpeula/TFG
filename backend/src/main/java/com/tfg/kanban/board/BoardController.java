package com.tfg.kanban.board;

import com.tfg.kanban.board.dto.BoardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Tableros", description = "Tableros Kanban y sus columnas (fijas: solo lectura)")
public class BoardController {

    private final BoardService boardService;

    @Operation(summary = "Tableros de un proyecto")
    @PreAuthorize("@access.canReadProject(#projectId)")
    @GetMapping("/api/projects/{projectId}/boards")
    public List<BoardResponse> findByProject(@PathVariable Long projectId) {
        return boardService.findByProject(projectId);
    }

    @Operation(summary = "Detalle del tablero",
            description = "Devuelve las columnas ordenadas con todas sus tareas. Es la llamada que usa el Kanban.")
    @PreAuthorize("@access.canReadBoard(#id)")
    @GetMapping("/api/boards/{id}")
    public BoardResponse findDetail(@PathVariable Long id) {
        return boardService.findDetail(id);
    }
}
