package com.tfg.kanban.project.dto;

import com.tfg.kanban.domain.model.Project;
import com.tfg.kanban.domain.model.ProjectStatus;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.team.dto.TeamSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Proyecto", example = """
        {"id":5,"name":"Practica 2 - Bases de datos","description":"Modelo E/R y SQL",
         "briefing":"Enunciado...","deadline":"2026-12-15","status":"EN_CURSO",
         "team":{"id":1,"name":"Grupo 4"},"boardCount":1,"myRole":"RESPONSABLE",
         "createdAt":"2026-08-21T10:15:30Z","updatedAt":"2026-08-21T11:02:00Z"}
        """)
public record ProjectResponse(
        Long id,
        String name,
        String description,
        String briefing,
        LocalDate deadline,
        ProjectStatus status,
        TeamSummary team,
        int boardCount,
        @Schema(description = "Rol del usuario autenticado en el equipo dueno del proyecto") Role myRole,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectResponse from(Project project, Role myRole) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
                project.getBriefing(), project.getDeadline(), project.getStatus(),
                TeamSummary.from(project.getTeam()), project.getBoards().size(), myRole,
                project.getCreatedAt(), project.getUpdatedAt());
    }
}
