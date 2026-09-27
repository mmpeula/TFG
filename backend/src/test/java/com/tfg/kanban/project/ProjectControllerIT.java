package com.tfg.kanban.project;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectControllerIT extends AbstractApiIT {

    @Test
    @DisplayName("Todo el equipo ve el proyecto de su workspace, cada uno con su rol")
    void theTeamReadsTheProject() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Practica de bases de datos");
        addMember(responsable, workspace.teamId(), miembro, Role.MIEMBRO);

        mockMvc.perform(authorized(get("/api/projects/{id}", workspace.id()), miembro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Practica de bases de datos"))
                .andExpect(jsonPath("$.status").value("EN_CURSO"))
                .andExpect(jsonPath("$.myRole").value("MIEMBRO"));
    }

    @Test
    @DisplayName("Quien no es del equipo no ve el proyecto")
    void outsidersCannotReadTheProject() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser ajeno = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Practica privada");

        mockMvc.perform(authorized(get("/api/projects/{id}", workspace.id()), ajeno))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Con el modulo de IA desactivado, generar responde 503 y no 500")
    void aiPlanReportsUnavailableWhenDisabled() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Practica con IA");

        // El perfil de test lleva app.ai.enabled=false: no se habla con ningun modelo.
        // Que el modelo no este no es un fallo del servidor, y por eso se distingue con un 503.
        mockMvc.perform(authorized(post("/api/workspaces/{id}/ai/plan", workspace.id()), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Practica de bases de datos con modelo E/R\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
    }

    @Test
    @DisplayName("La actividad del proyecto registra la creacion")
    void activityIsRecorded() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Practica con historial");

        mockMvc.perform(authorized(get("/api/projects/{id}/activity", workspace.id()), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("CREACION"))
                .andExpect(jsonPath("$.content[0].entityType").value("Project"));
    }
}
