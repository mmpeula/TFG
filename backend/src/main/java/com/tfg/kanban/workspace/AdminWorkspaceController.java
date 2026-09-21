package com.tfg.kanban.workspace;

import com.tfg.kanban.common.ApiError;
import com.tfg.kanban.team.dto.UpdateMemberRoleRequest;
import com.tfg.kanban.workspace.dto.AdminWorkspaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Soporte sobre los workspaces de toda la plataforma. Solo rol ADMIN.
 *
 * <p>Deliberadamente no hay aqui ningun endpoint que devuelva tareas,
 * comentarios o ficheros: para eso hace falta pertenecer al equipo, y un
 * administrador no pertenece a ninguno. Lo que hay es ver el estado de un
 * workspace y las acciones para desatascarlo, pararlo o borrarlo.</p>
 */
@RestController
@RequestMapping("/api/admin/workspaces")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administracion", description = "Soporte sobre las cuentas y los workspaces de la plataforma")
public class AdminWorkspaceController {

    private final AdminWorkspaceService adminWorkspaceService;

    @Operation(summary = "Todos los workspaces de la plataforma",
            description = """
                    Datos de diagnostico para dar soporte: quien esta en cada equipo, cuantas
                    tareas hay y cuando se movio por ultima vez. No incluye el contenido del
                    tablero. Marca como bloqueados los que se han quedado sin ninguna persona
                    responsable con la cuenta activa.
                    """)
    @GetMapping
    public List<AdminWorkspaceResponse> findAll() {
        return adminWorkspaceService.findAll();
    }

    @Operation(summary = "Cambiar el rol de un miembro del workspace",
            description = """
                    Sirve para desatascar un workspace que se ha quedado sin nadie que pueda
                    administrarlo, y tambien para cualquier otro cambio de rol. Solo se puede
                    cambiar el rol de quien ya pertenece al equipo, y el equipo nunca se queda
                    sin ninguna persona responsable.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rol cambiado"),
            @ApiResponse(responseCode = "409",
                    description = "No pertenece al equipo, su cuenta esta deshabilitada, el rol no "
                            + "encaja con el tipo de cuenta, o el equipo se quedaria sin responsable",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{workspaceId}/members/{userId}/role")
    public ResponseEntity<Void> cambiarRol(@PathVariable Long workspaceId, @PathVariable Long userId,
                                           @Valid @RequestBody UpdateMemberRoleRequest request) {
        adminWorkspaceService.cambiarRol(workspaceId, userId, request.role());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Suspender o reactivar un workspace",
            description = """
                    Suspenderlo deja al equipo sin poder abrirlo ni tocar nada suyo, pero no borra
                    nada: reactivarlo lo devuelve tal cual estaba. Es la alternativa a borrarlo
                    cuando solo hace falta pararlo un tiempo.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Workspace suspendido o reactivado"))
    @PutMapping("/{workspaceId}/enabled")
    public ResponseEntity<Void> setEnabled(@PathVariable Long workspaceId,
                                           @Valid @RequestBody SetEnabledRequest request) {
        adminWorkspaceService.setEnabled(workspaceId, request.enabled());
        return ResponseEntity.noContent().build();
    }

    /** Si el workspace queda operativo o suspendido. */
    public record SetEnabledRequest(@NotNull Boolean enabled) {
    }

    @Operation(summary = "Borrar un workspace",
            description = "Borra el workspace y su equipo si se queda vacio. No se puede deshacer.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Workspace borrado"))
    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> delete(@PathVariable Long workspaceId) {
        adminWorkspaceService.delete(workspaceId);
        return ResponseEntity.noContent().build();
    }
}
