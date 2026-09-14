package com.tfg.kanban.workspace;

import com.tfg.kanban.common.ApiError;
import com.tfg.kanban.workspace.dto.CreateWorkspaceRequest;
import com.tfg.kanban.workspace.dto.UpdateWorkspaceRequest;
import com.tfg.kanban.workspace.dto.WorkspaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Workspaces: la puerta de entrada de la aplicacion.
 *
 * <p>Es una fachada sobre equipos, proyectos y tableros pensada para que el
 * estudiante no tenga que conocer esa jerarquia. Los endpoints de
 * {@code /api/teams}, {@code /api/projects} y {@code /api/boards} siguen
 * existiendo y funcionando igual.</p>
 */
@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspaces", description = "Asignaturas, practicas y trabajos del estudiante")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @Operation(summary = "Mis workspaces",
            description = "Todos los workspaces de los que el usuario es miembro, con sus numeros de tarjeta.")
    @GetMapping
    public List<WorkspaceResponse> findMine() {
        return workspaceService.findMine();
    }

    @Operation(summary = "Un workspace")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workspace encontrado"),
            @ApiResponse(responseCode = "403", description = "No se pertenece a este workspace",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize("@access.canReadProject(#workspaceId)")
    @GetMapping("/{workspaceId}")
    public WorkspaceResponse findById(@PathVariable Long workspaceId) {
        return workspaceService.findById(workspaceId);
    }

    @Operation(summary = "Crear un workspace",
            description = """
                    Crea de una vez el equipo, el proyecto y el tablero con sus columnas
                    'Por hacer', 'En progreso' y 'Hecho'. Quien lo crea queda como RESPONSABLE.
                    """)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public WorkspaceResponse create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return workspaceService.create(request);
    }

    @Operation(summary = "Editar un workspace")
    @PreAuthorize("@access.canManageProject(#workspaceId)")
    @PutMapping("/{workspaceId}")
    public WorkspaceResponse update(@PathVariable Long workspaceId,
                                    @Valid @RequestBody UpdateWorkspaceRequest request) {
        return workspaceService.update(workspaceId, request);
    }

    @Operation(summary = "Borrar un workspace",
            description = "Arrastra tablero, columnas, tareas, comentarios, adjuntos e historial.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@access.canManageProject(#workspaceId)")
    @DeleteMapping("/{workspaceId}")
    public void delete(@PathVariable Long workspaceId) {
        workspaceService.delete(workspaceId);
    }
}
