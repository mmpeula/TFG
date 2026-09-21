package com.tfg.kanban.team.dto;

import com.tfg.kanban.domain.model.Invitation;
import com.tfg.kanban.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Una invitacion vista por quien la recibe: lo que hace falta para decidir si
 * entrar o no, mas el token con el que responder.
 */
@Schema(description = "Invitacion recibida, a la espera de respuesta", example = """
        {"token":"9f2c...","teamId":7,"teamName":"Practica de Bases de Datos",
         "role":"MIEMBRO","invitedByName":"Ana Garcia","createdAt":"2026-09-20T10:15:30Z"}
        """)
public record MyInvitationResponse(
        String token,
        Long teamId,
        String teamName,
        Role role,
        String invitedByName,
        Instant createdAt
) {

    public static MyInvitationResponse from(Invitation invitation) {
        return new MyInvitationResponse(
                invitation.getToken(),
                invitation.getTeam().getId(),
                invitation.getTeam().getName(),
                invitation.getRole(),
                invitation.getInvitedBy() == null ? null : invitation.getInvitedBy().getFullName(),
                invitation.getCreatedAt());
    }
}
