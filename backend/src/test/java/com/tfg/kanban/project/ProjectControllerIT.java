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
    @DisplayName("El RESPONSABLE crea el proyecto y todo el equipo puede verlo")
    void createAndReadProject() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo proyecto");
        addMember(responsable, teamId, miembro, Role.MIEMBRO);

        Long projectId = createProject(responsable, teamId, "Practica de bases de datos");

        mockMvc.perform(authorized(get("/api/projects/{id}", projectId), miembro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Practica de bases de datos"))
                .andExpect(jsonPath("$.status").value("PLANIFICACION"))
                .andExpect(jsonPath("$.myRole").value("MIEMBRO"));

        mockMvc.perform(authorized(get("/api/projects").param("teamId", teamId.toString()), miembro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("Un MIEMBRO no puede crear, editar ni borrar proyectos")
    void memberCannotManageProjects() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo RBAC");
        addMember(responsable, teamId, miembro, Role.MIEMBRO);
        Long projectId = createProject(responsable, teamId, "Practica RBAC");

        mockMvc.perform(authorized(post("/api/projects"), miembro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":%d,"name":"No deberia crearse"}
                                """.formatted(teamId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(put("/api/projects/{id}", projectId), miembro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renombrado por un miembro"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(delete("/api/projects/{id}", projectId), miembro))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El listado de proyectos filtra por equipo y por estado")
    void listFiltersByTeamAndStatus() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo filtros");
        Long enCurso = createProject(responsable, teamId, "Practica en curso");
        createProject(responsable, teamId, "Practica planificada");

        mockMvc.perform(authorized(put("/api/projects/{id}", enCurso), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Practica en curso\",\"status\":\"EN_CURSO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_CURSO"));

        mockMvc.perform(authorized(get("/api/projects"), responsable)
                        .param("teamId", teamId.toString())
                        .param("status", "EN_CURSO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(enCurso));

        mockMvc.perform(authorized(get("/api/projects"), responsable)
                        .param("status", "ENTREGADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(authorized(get("/api/projects"), responsable)
                        .param("teamId", teamId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("No se pueden repetir nombres de proyecto dentro del mismo equipo")
    void rejectsDuplicatedProjectName() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo duplicados");
        createProject(responsable, teamId, "Practica repetida");

        mockMvc.perform(authorized(post("/api/projects"), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":%d,"name":"Practica repetida"}
                                """.formatted(teamId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Con el modulo de IA desactivado, generar responde 503 y no 500")
    void aiPlanReportsUnavailableWhenDisabled() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo IA");
        Long projectId = createProject(responsable, teamId, "Practica con IA");

        // El perfil de test lleva app.ai.enabled=false: no se habla con ningun modelo.
        // Que el modelo no este no es un fallo del servidor, y por eso se distingue con un 503.
        mockMvc.perform(authorized(post("/api/projects/{id}/ai-plan", projectId), responsable))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
    }

    @Test
    @DisplayName("La actividad del proyecto registra la creacion")
    void activityIsRecorded() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo actividad");
        Long projectId = createProject(responsable, teamId, "Practica con historial");

        mockMvc.perform(authorized(get("/api/projects/{id}/activity", projectId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("CREACION"))
                .andExpect(jsonPath("$.content[0].entityType").value("Project"));
    }
}
