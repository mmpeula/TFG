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
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
@Tag(name = "Espacios de trabajo", description = "Asignaturas, practicas y trabajos del estudiante")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @Operation(summary = "Mis espacios de trabajo",
            description = "Todos los espacios de trabajo de los que el usuario es miembro, con sus numeros de tarjeta.")
    @GetMapping
    public List<WorkspaceResponse> findMine() {
        return workspaceService.findMine();
    }

    @Operation(summary = "Un espacio de trabajo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Espacio de trabajo encontrado"),
            @ApiResponse(responseCode = "403", description = "No se pertenece a este espacio de trabajo",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize("@access.canReadProject(#workspaceId)")
    @GetMapping("/{workspaceId}")
    public WorkspaceResponse findById(@PathVariable Long workspaceId) {
        return workspaceService.findById(workspaceId);
    }

    @Operation(summary = "Crear un espacio de trabajo",
            description = """
                    Crea de una vez el equipo, el proyecto y el tablero con sus columnas
                    'Por hacer', 'En progreso' y 'Hecho'. Quien lo crea queda como RESPONSABLE.
                    """)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public WorkspaceResponse create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return workspaceService.create(request);
    }

    @Operation(summary = "Editar un espacio de trabajo")
    @PreAuthorize("@access.canManageProject(#workspaceId)")
    @PutMapping("/{workspaceId}")
    public WorkspaceResponse update(@PathVariable Long workspaceId,
                                    @Valid @RequestBody UpdateWorkspaceRequest request) {
        return workspaceService.update(workspaceId, request);
    }

    @Operation(summary = "Borrar un espacio de trabajo",
            description = "Arrastra tablero, columnas, tareas, comentarios e historial.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@access.canManageProject(#workspaceId)")
    @DeleteMapping("/{workspaceId}")
    public void delete(@PathVariable Long workspaceId) {
        workspaceService.delete(workspaceId);
    }

    @Operation(summary = "Subir la foto del espacio de trabajo",
            description = "Solo el RESPONSABLE. Peticion multipart/form-data con el campo 'file'; "
                    + "sustituye la foto anterior si habia una.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(type = "object", requiredProperties = "file")))
    @PreAuthorize("@access.canManageProject(#workspaceId)")
    @PostMapping(value = "/{workspaceId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkspaceResponse uploadPhoto(@PathVariable Long workspaceId, @RequestPart("file") MultipartFile file) {
        return workspaceService.uploadCover(workspaceId, file);
    }

    @Operation(summary = "Descargar la foto del espacio de trabajo",
            description = "Cualquier miembro del equipo puede verla.")
    @PreAuthorize("@access.canReadProject(#workspaceId)")
    @GetMapping("/{workspaceId}/photo")
    public ResponseEntity<Resource> downloadPhoto(@PathVariable Long workspaceId) {
        WorkspaceService.CoverFile cover = workspaceService.loadCover(workspaceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        cover.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : cover.contentType())
                .body(cover.resource());
    }

    @Operation(summary = "Quitar la foto del espacio de trabajo",
            description = "Solo el RESPONSABLE. Vuelve a mostrarse con su color e icono.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Foto eliminada"))
    @PreAuthorize("@access.canManageProject(#workspaceId)")
    @DeleteMapping("/{workspaceId}/photo")
    public ResponseEntity<Void> removePhoto(@PathVariable Long workspaceId) {
        workspaceService.removeCover(workspaceId);
        return ResponseEntity.noContent().build();
    }
}
