package com.tfg.kanban.team;

import com.tfg.kanban.common.ApiError;
import com.tfg.kanban.team.dto.InvitationResponse;
import com.tfg.kanban.team.dto.InviteCandidateResponse;
import com.tfg.kanban.team.dto.InviteRequest;
import com.tfg.kanban.team.dto.MyInvitationResponse;
import com.tfg.kanban.team.dto.TeamMemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

/**
 * Invitaciones a un equipo.
 *
 * <p>Invitar y cancelar son cosa del RESPONSABLE del equipo; responder, solo de
 * quien ha sido invitado, y eso se comprueba en el servicio contra el token
 * porque no depende de ningun rol.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Invitaciones", description = "Invitar a alguien a un equipo y responder a la invitacion")
public class InvitationController {

    private final InvitationService invitationService;

    @Operation(summary = "Invitar a alguien al equipo",
            description = """
                    Solo el RESPONSABLE. Manda un correo con el enlace para aceptar. La persona
                    NO entra en el equipo hasta que acepta: hasta entonces figura como pendiente.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Invitacion enviada"),
            @ApiResponse(responseCode = "404", description = "No hay ninguna cuenta con ese email",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Ya esta en el equipo o ya tiene una invitacion pendiente",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize("@access.canManageTeam(#teamId)")
    @PostMapping("/api/teams/{teamId}/invitations")
    public ResponseEntity<InvitationResponse> invite(@PathVariable Long teamId,
                                                     @Valid @RequestBody InviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.invite(teamId, request));
    }

    @Operation(summary = "Con que roles se puede invitar a un correo",
            description = """
                    Solo el RESPONSABLE. Dice si la cuenta de ese correo se puede invitar como docente,
                    como alumno o con cualquiera de los dos (una cuenta docente admite las dos), para
                    ofrecer la eleccion solo cuando existe. No crea nada.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No hay ninguna cuenta con ese email",
            content = @Content(schema = @Schema(implementation = ApiError.class))))
    @PreAuthorize("@access.canManageTeam(#teamId)")
    @GetMapping("/api/teams/{teamId}/invitations/candidate")
    public InviteCandidateResponse candidate(@PathVariable Long teamId, @RequestParam String email) {
        return invitationService.candidate(email);
    }

    @Operation(summary = "Invitaciones pendientes del equipo",
            description = "Las que aun no han respondido. Las ve cualquier miembro del equipo.")
    @PreAuthorize("@access.isTeamMember(#teamId)")
    @GetMapping("/api/teams/{teamId}/invitations")
    public List<InvitationResponse> pending(@PathVariable Long teamId) {
        return invitationService.pendingOf(teamId);
    }

    @Operation(summary = "Cancelar una invitacion", description = "Solo el RESPONSABLE, y solo si sigue pendiente.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Invitacion cancelada"))
    @PreAuthorize("@access.canManageTeam(#teamId)")
    @DeleteMapping("/api/teams/{teamId}/invitations/{invitationId}")
    public ResponseEntity<Void> cancel(@PathVariable Long teamId, @PathVariable Long invitationId) {
        invitationService.cancel(teamId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mis invitaciones pendientes",
            description = "Las que esperan respuesta del usuario autenticado, para avisarle al entrar.")
    @GetMapping("/api/invitations/mine")
    public List<MyInvitationResponse> mine() {
        return invitationService.mine();
    }

    @Operation(summary = "Aceptar una invitacion",
            description = "Solo quien fue invitado. Al aceptar, entra en el equipo con el rol de la invitacion.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ya es miembro del equipo"),
            @ApiResponse(responseCode = "404", description = "La invitacion no existe, no es suya o ya no vale",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/api/invitations/{token}/accept")
    public TeamMemberResponse accept(@PathVariable String token) {
        return invitationService.accept(token);
    }

    @Operation(summary = "Rechazar una invitacion", description = "Solo quien fue invitado.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Invitacion rechazada"))
    @PostMapping("/api/invitations/{token}/reject")
    public ResponseEntity<Void> reject(@PathVariable String token) {
        invitationService.reject(token);
        return ResponseEntity.noContent().build();
    }
}
