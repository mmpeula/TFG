package com.tfg.kanban.team;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamControllerIT extends AbstractApiIT {

    @Test
    @DisplayName("Quien crea el equipo queda como RESPONSABLE y lo ve en su listado")
    void createTeamMakesCreatorResponsable() throws Exception {
        TestUser creator = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(creator, "Grupo de prueba");

        mockMvc.perform(authorized(get("/api/teams/{id}", teamId), creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("RESPONSABLE"))
                .andExpect(jsonPath("$.memberCount").value(1));

        mockMvc.perform(authorized(get("/api/teams"), creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(teamId)).exists());
    }

    @Test
    @DisplayName("Un extrano no puede ver el equipo")
    void outsiderCannotSeeTeam() throws Exception {
        TestUser owner = registerUser(Role.MIEMBRO);
        TestUser outsider = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(owner, "Equipo privado");

        mockMvc.perform(authorized(get("/api/teams/{id}", teamId), outsider))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("El RESPONSABLE anade miembros y les cambia el rol; el MIEMBRO no puede")
    void memberManagementIsRestrictedToResponsable() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        TestUser otro = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo con miembros");

        addMember(responsable, teamId, miembro, Role.MIEMBRO);

        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), miembro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // El MIEMBRO no puede invitar a nadie
        mockMvc.perform(authorized(post("/api/teams/{id}/members", teamId), miembro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","role":"MIEMBRO"}
                                """.formatted(otro.email())))
                .andExpect(status().isForbidden());

        // El RESPONSABLE si puede ascender a otro miembro
        mockMvc.perform(authorized(patch("/api/teams/{id}/members/{userId}", teamId, miembro.id()), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"RESPONSABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RESPONSABLE"));
    }

    @Test
    @DisplayName("No se puede dejar al equipo sin ningun RESPONSABLE")
    void cannotRemoveLastResponsable() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo de una persona");

        mockMvc.perform(authorized(patch("/api/teams/{id}/members/{userId}", teamId, responsable.id()), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MIEMBRO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Quien creo el equipo puede salirse si deja otro responsable, y el equipo pasa a esa persona")
    void theCreatorCanLeaveHandingOverTheTeam() throws Exception {
        TestUser creador = registerUser(Role.MIEMBRO);
        TestUser companero = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(creador, "Equipo que cambia de manos");
        addMember(creador, teamId, companero, Role.RESPONSABLE);

        mockMvc.perform(authorized(delete("/api/teams/{id}/members/{userId}", teamId, creador.id()), creador))
                .andExpect(status().isNoContent());

        // Con Team.members en cascade + orphanRemoval, un borrado por repositorio se
        // deshacia solo al volcar los cambios y esto devolvia 204 sin borrar nada.
        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), companero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.id").value(companero.id()));

        // Y quien queda es ya el unico responsable: no puede irse tambien.
        mockMvc.perform(authorized(delete("/api/teams/{id}/members/{userId}", teamId, companero.id()), companero))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("No se borra un equipo que aun tiene proyectos")
    void cannotDeleteTeamWithProjects() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo con proyecto");
        createProject(responsable, teamId, "Practica 1");

        mockMvc.perform(authorized(delete("/api/teams/{id}", teamId), responsable))
                .andExpect(status().isConflict());
    }
}
