package com.tfg.kanban.team.dto;

import com.tfg.kanban.domain.model.Invitation;
import com.tfg.kanban.domain.model.InvitationStatus;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Una invitacion vista desde el equipo, para la pantalla de miembros.
 *
 * @param emailSent si el aviso por correo llego a salir. Se dice tal cual para
 *                  no dar por enviado algo que no lo esta: la invitacion vale
 *                  igual, porque quien la recibe la ve al entrar en la aplicacion.
 */
@Schema(description = "Invitacion pendiente de respuesta", example = """
        {"id":12,"invited":{"id":4,"fullName":"Luis Perez","email":"luis@uni.es"},
         "role":"MIEMBRO","status":"PENDIENTE","invitedByName":"Ana Garcia",
         "emailSent":true,"createdAt":"2026-09-20T10:15:30Z"}
        """)
public record InvitationResponse(
        Long id,
        UserSummary invited,
        Role role,
        InvitationStatus status,
        String invitedByName,
        boolean emailSent,
        Instant createdAt
) {

    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                UserSummary.from(invitation.getInvited()),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.getInvitedBy() == null ? null : invitation.getInvitedBy().getFullName(),
                invitation.isEmailSent(),
                invitation.getCreatedAt());
    }
}
