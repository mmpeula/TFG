package com.tfg.kanban.task;

import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.domain.model.TaskPriority;
import com.tfg.kanban.task.dto.MoveTaskRequest;
import com.tfg.kanban.task.dto.TaskRequest;
import com.tfg.kanban.task.dto.TaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "Tareas", description = "Tarjetas del tablero Kanban")
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "Buscar tareas",
            description = """
                    Listado paginado con filtros opcionales. Solo devuelve tareas de proyectos
                    en los que el usuario autenticado es miembro del equipo.
                    """)
    @GetMapping("/api/tasks")
    public PageResponse<TaskResponse> search(
            @Parameter(description = "Filtrar por proyecto") @RequestParam(required = false) Long projectId,
            @Parameter(description = "Filtrar por tablero") @RequestParam(required = false) Long boardId,
            @Parameter(description = "Filtrar por columna") @RequestParam(required = false) Long columnId,
            @Parameter(description = "Filtrar por persona asignada") @RequestParam(required = false) Long assigneeId,
            @Parameter(description = "Filtrar por prioridad") @RequestParam(required = false) TaskPriority priority,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return taskService.search(projectId, boardId, columnId, assigneeId, priority, pageable);
    }

    @Operation(summary = "Detalle de una tarea")
    @PreAuthorize("@access.canReadTask(#id)")
    @GetMapping("/api/tasks/{id}")
    public TaskResponse findById(@PathVariable Long id) {
        return taskService.findById(id);
    }

    @Operation(summary = "Crear una tarea en una columna",
            description = "RESPONSABLE o MIEMBRO.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tarea creada"),
            @ApiResponse(responseCode = "403", description = "Los DOCENTE no pueden crear tareas")
    })
    @PreAuthorize("@access.canWriteColumn(#columnId)")
    @PostMapping("/api/columns/{columnId}/tasks")
    public ResponseEntity<TaskResponse> create(@PathVariable Long columnId,
                                               @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(columnId, request));
    }

    @Operation(summary = "Editar una tarea", description = "RESPONSABLE o MIEMBRO del equipo.")
    @PreAuthorize("@access.canWriteTask(#id)")
    @PutMapping("/api/tasks/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return taskService.update(id, request);
    }

    @Operation(summary = "Mover o reordenar una tarea",
            description = "Es la llamada que persiste el drag & drop del tablero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tarea movida"),
            @ApiResponse(responseCode = "409", description = "La columna destino es de otro tablero")
    })
    @PreAuthorize("@access.canWriteTask(#id)")
    @PatchMapping("/api/tasks/{id}/move")
    public TaskResponse move(@PathVariable Long id, @Valid @RequestBody MoveTaskRequest request) {
        return taskService.move(id, request);
    }

    @Operation(summary = "Borrar una tarea", description = "RESPONSABLE o MIEMBRO del equipo.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Tarea borrada"))
    @PreAuthorize("@access.canWriteTask(#id)")
    @DeleteMapping("/api/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
