package com.tfg.kanban.team;

import com.tfg.kanban.team.dto.TeamMemberResponse;
import com.tfg.kanban.team.dto.UpdateMemberNotesRequest;
import com.tfg.kanban.team.dto.UpdateMemberRoleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Quien esta en el equipo de un workspace y con que rol. El equipo lo crea y lo
 * borra su workspace (/api/workspaces), y se entra en el por invitacion
 * (/api/teams/{id}/invitations): aqui no hay forma de meter a nadie sin que acepte.
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name = "Equipos", description = "Miembros de cada equipo y su rol")
public class TeamController {

    private final TeamService teamService;

    @Operation(summary = "Miembros del equipo")
    @PreAuthorize("@access.isTeamMember(#id)")
    @GetMapping("/{id}/members")
    public List<TeamMemberResponse> findMembers(@PathVariable Long id) {
        return teamService.findMembers(id);
    }

    @Operation(summary = "Cambiar el rol de un miembro",
            description = "Solo el RESPONSABLE. No se puede dejar al equipo sin ningun RESPONSABLE.")
    @PreAuthorize("@access.canManageTeam(#id)")
    @PatchMapping("/{id}/members/{userId}")
    public TeamMemberResponse updateMemberRole(@PathVariable Long id, @PathVariable Long userId,
                                               @Valid @RequestBody UpdateMemberRoleRequest request) {
        return teamService.updateMemberRole(id, userId, request);
    }

    @Operation(summary = "Indicaciones permanentes de un miembro para generar el plan",
            description = "Lo que se tiene en cuenta de esa persona al repartir trabajo: en que "
                    + "se maneja, que prefiere y que conviene evitarle. Se guarda y vale para todas las "
                    + "ejecuciones siguientes. Solo el RESPONSABLE.")
    @PreAuthorize("@access.canManageTeam(#id)")
    @PatchMapping("/{id}/members/{userId}/notes")
    public TeamMemberResponse updateMemberNotes(@PathVariable Long id, @PathVariable Long userId,
                                                @Valid @RequestBody UpdateMemberNotesRequest request) {
        return teamService.updateMemberNotes(id, userId, request);
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
