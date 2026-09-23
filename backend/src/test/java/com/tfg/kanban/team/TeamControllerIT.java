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
    @DisplayName("Las indicaciones para el plan se guardan y se leen con el miembro")
    void planNotesSurviveAndTravelWithTheMember() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser companero = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo con fichas");
        addMember(responsable, teamId, companero, Role.MIEMBRO);

        mockMvc.perform(authorized(
                        patch("/api/teams/{id}/members/{userId}/notes", teamId, companero.id()), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planNotes\":\"Se maneja con Java y Spring. Evitar frontend.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planNotes").value("Se maneja con Java y Spring. Evitar frontend."));

        // Se queda: es lo que la distingue de las indicaciones de una ejecucion.
        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.user.id == %d)].planNotes".formatted(companero.id()))
                        .value("Se maneja con Java y Spring. Evitar frontend."));

        // Y un texto en blanco las borra en vez de dejar una cadena vacia.
        mockMvc.perform(authorized(
                        patch("/api/teams/{id}/members/{userId}/notes", teamId, companero.id()), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planNotes\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planNotes").doesNotExist());
    }

    @Test
    @DisplayName("Solo el responsable escribe las indicaciones de un miembro")
    void onlyResponsableWritesPlanNotes() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser companero = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo con fichas");
        addMember(responsable, teamId, companero, Role.MIEMBRO);

        // Ni siquiera la suya propia: la ficha la mantiene quien coordina el equipo,
        // igual que el rol, porque es lo que decide como se le reparte el trabajo.
        mockMvc.perform(authorized(
                        patch("/api/teams/{id}/members/{userId}/notes", teamId, companero.id()), companero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planNotes\":\"Solo quiero tareas faciles\"}"))
                .andExpect(status().isForbidden());

        TestUser ajeno = registerUser(Role.MIEMBRO);
        mockMvc.perform(authorized(
                        patch("/api/teams/{id}/members/{userId}/notes", teamId, companero.id()), ajeno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planNotes\":\"Me invento una ficha\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Una ficha desmedida se rechaza por validacion")
    void planNotesAreCapped() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo con fichas");

        mockMvc.perform(authorized(
                        patch("/api/teams/{id}/members/{userId}/notes", teamId, responsable.id()), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("planNotes", "a".repeat(601)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
