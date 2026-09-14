package com.tfg.kanban.team.dto;

import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.Team;
import com.tfg.kanban.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Equipo de trabajo", example = """
        {"id":1,"name":"Grupo 4","description":"Practicas 2026/27",
         "owner":{"id":1,"fullName":"Ana Garcia","email":"ana@uni.es"},
         "memberCount":4,"projectCount":2,"myRole":"RESPONSABLE","createdAt":"2026-08-21T10:15:30Z"}
        """)
public record TeamResponse(
        Long id,
        String name,
        String description,
        UserSummary owner,
        int memberCount,
        int projectCount,
        @Schema(description = "Rol del usuario autenticado dentro de este equipo") Role myRole,
        Instant createdAt
) {

    public static TeamResponse from(Team team, Role myRole) {
        return new TeamResponse(team.getId(), team.getName(), team.getDescription(),
                UserSummary.from(team.getOwner()), team.getMembers().size(), team.getProjects().size(),
                myRole, team.getCreatedAt());
    }
}
