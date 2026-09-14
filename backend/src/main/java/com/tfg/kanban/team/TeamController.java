package com.tfg.kanban.team;

import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.team.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name = "Equipos", description = "Equipos de trabajo y sus miembros")
public class TeamController {

    private final TeamService teamService;

    @Operation(summary = "Mis equipos", description = "Equipos en los que participa el usuario autenticado.")
    @GetMapping
    public PageResponse<TeamResponse> findMine(
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return teamService.findMine(pageable);
    }

    @Operation(summary = "Crear un equipo",
            description = "Cualquier usuario autenticado puede crear un equipo; queda como RESPONSABLE del mismo.")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Equipo creado"))
    @PostMapping
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody TeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.create(request));
    }

    @Operation(summary = "Detalle de un equipo")
    @PreAuthorize("@access.isTeamMember(#id)")
    @GetMapping("/{id}")
    public TeamResponse findById(@PathVariable Long id) {
        return teamService.findById(id);
    }

    @Operation(summary = "Editar un equipo", description = "Solo el RESPONSABLE del equipo.")
    @PreAuthorize("@access.canManageTeam(#id)")
    @PutMapping("/{id}")
    public TeamResponse update(@PathVariable Long id, @Valid @RequestBody TeamRequest request) {
        return teamService.update(id, request);
    }

    @Operation(summary = "Borrar un equipo",
            description = "Solo el RESPONSABLE y solo si el equipo no tiene proyectos.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Equipo borrado"),
            @ApiResponse(responseCode = "409", description = "El equipo aun tiene proyectos")
    })
    @PreAuthorize("@access.canManageTeam(#id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        teamService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Miembros del equipo")
    @PreAuthorize("@access.isTeamMember(#id)")
    @GetMapping("/{id}/members")
    public List<TeamMemberResponse> findMembers(@PathVariable Long id) {
        return teamService.findMembers(id);
    }

    @Operation(summary = "Anadir un miembro",
            description = "Solo el RESPONSABLE. El usuario debe estar registrado; se busca por email.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Miembro anadido"),
            @ApiResponse(responseCode = "404", description = "No hay ningun usuario con ese email"),
            @ApiResponse(responseCode = "409", description = "El usuario ya pertenece al equipo")
    })
    @PreAuthorize("@access.canManageTeam(#id)")
    @PostMapping("/{id}/members")
    public ResponseEntity<TeamMemberResponse> addMember(@PathVariable Long id,
                                                        @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.addMember(id, request));
    }

    @Operation(summary = "Cambiar el rol de un miembro",
            description = "Solo el RESPONSABLE. No se puede dejar al equipo sin ningun RESPONSABLE.")
    @PreAuthorize("@access.canManageTeam(#id)")
    @PatchMapping("/{id}/members/{userId}")
    public TeamMemberResponse updateMemberRole(@PathVariable Long id, @PathVariable Long userId,
                                               @Valid @RequestBody UpdateMemberRoleRequest request) {
        return teamService.updateMemberRole(id, userId, request);
    }

    @Operation(summary = "Expulsar a un miembro o salirse del equipo",
            description = "El RESPONSABLE puede expulsar a cualquiera; cualquier miembro puede salirse el mismo.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Miembro eliminado"))
    @PreAuthorize("@access.canManageTeam(#id) or @access.isSelf(#userId)")
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        teamService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }
}
