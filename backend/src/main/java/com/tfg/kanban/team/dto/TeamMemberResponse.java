package com.tfg.kanban.team.dto;

import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.TeamMember;
import com.tfg.kanban.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Miembro de un equipo con su rol en ese equipo", example = """
        {"id":3,"user":{"id":1,"fullName":"Ana Garcia","email":"ana@uni.es"},
         "role":"RESPONSABLE","joinedAt":"2026-08-21T10:15:30Z",
         "planNotes":"Se maneja con React y le gusta la parte visual."}
        """)
public record TeamMemberResponse(
        Long id,
        UserSummary user,
        Role role,
        Instant joinedAt,

        @Schema(description = "Lo que se sabe de esta persona al repartir trabajo en un plan. "
                + "Se guarda y vale para todas las ejecuciones.")
        String planNotes
) {

    public static TeamMemberResponse from(TeamMember member) {
        return new TeamMemberResponse(member.getId(), UserSummary.from(member.getUser()),
                member.getRole(), member.getCreatedAt(), member.getPlanNotes());
    }
}
