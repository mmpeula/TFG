package com.tfg.kanban.comment;

import com.tfg.kanban.comment.dto.CommentRequest;
import com.tfg.kanban.comment.dto.CommentResponse;
import com.tfg.kanban.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Comentarios", description = "Conversacion sobre una tarea")
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "Comentarios de una tarea", description = "Cualquier miembro del equipo puede leerlos.")
    @PreAuthorize("@access.canReadTask(#taskId)")
    @GetMapping("/api/tasks/{taskId}/comments")
    public PageResponse<CommentResponse> findByTask(
            @PathVariable Long taskId,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return commentService.findByTask(taskId, pageable);
    }

    @Operation(summary = "Comentar una tarea", description = "RESPONSABLE o MIEMBRO; los DOCENTE no comentan.")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Comentario creado"))
    @PreAuthorize("@access.canWriteTask(#taskId)")
    @PostMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<CommentResponse> create(@PathVariable Long taskId,
                                                  @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(taskId, request));
    }

    @Operation(summary = "Editar un comentario", description = "Solo su autor o el RESPONSABLE del equipo.")
    @PreAuthorize("@access.canModifyComment(#id)")
    @PutMapping("/api/comments/{id}")
    public CommentResponse update(@PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        return commentService.update(id, request);
    }

    @Operation(summary = "Borrar un comentario", description = "Solo su autor o el RESPONSABLE del equipo.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Comentario borrado"))
    @PreAuthorize("@access.canModifyComment(#id)")
    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
