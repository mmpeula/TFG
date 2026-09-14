package com.tfg.kanban.team.dto;

import com.tfg.kanban.domain.model.Team;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Equipo resumido", example = "{\"id\":1,\"name\":\"Grupo 4\"}")
public record TeamSummary(Long id, String name) {

    public static TeamSummary from(Team team) {
        return team == null ? null : new TeamSummary(team.getId(), team.getName());
    }
}
