package com.tfg.kanban.team;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamControllerIT extends AbstractApiIT {

    @Test
    @DisplayName("Quien crea el workspace queda como unico RESPONSABLE de su equipo")
    void creatorIsTheResponsable() throws Exception {
        TestUser creator = registerUser(Role.MIEMBRO);
        Long teamId = createWorkspace(creator, "Grupo de prueba").teamId();

        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("RESPONSABLE"));
    }

    @Test
    @DisplayName("Un extrano no puede ver quien esta en el equipo")
    void outsiderCannotSeeTeam() throws Exception {
        TestUser owner = registerUser(Role.MIEMBRO);
        TestUser outsider = registerUser(Role.MIEMBRO);
        Long teamId = createWorkspace(owner, "Equipo privado").teamId();

        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), outsider))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Nadie entra en un equipo sin invitacion: no hay forma de anadir a alguien directamente")
    void thereIsNoWayToAddSomeoneWithoutAnInvitation() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser otro = registerUser(Role.MIEMBRO);
        Long teamId = createWorkspace(responsable, "Equipo cerrado").teamId();

        mockMvc.perform(authorized(post("/api/teams/{id}/members", teamId), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","role":"MIEMBRO"}
                                """.formatted(otro.email())))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), responsable))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("El RESPONSABLE invita y cambia roles; el MIEMBRO no puede")
    void memberManagementIsRestrictedToResponsable() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        TestUser otro = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo con miembros");
        Long teamId = workspace.teamId();

        addMember(responsable, teamId, miembro, Role.MIEMBRO);

        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), miembro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // El MIEMBRO no puede invitar a nadie
        mockMvc.perform(authorized(post("/api/teams/{id}/invitations", teamId), miembro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
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
        Workspace workspace = createWorkspace(responsable, "Equipo de una persona");
        Long teamId = workspace.teamId();

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
        Workspace workspace = createWorkspace(creador, "Equipo que cambia de manos");
        Long teamId = workspace.teamId();
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
    @DisplayName("Al salir del equipo se sueltan sus tareas sin terminar; en las terminadas sigue constando")
    void leavingReleasesOpenTasksButKeepsDoneOnes() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser companero = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo con una baja");
        Long teamId = workspace.teamId();
        addMember(responsable, teamId, companero, Role.MIEMBRO);
        long[] columns = columnIds(responsable, workspace.boardId());
        long hecho = columns[columns.length - 1];

        Long soloSuya = createTask(responsable, columns[0], "Solo suya");
        Long compartida = createTask(responsable, columns[1], "Compartida");
        Long terminada = createTask(responsable, hecho, "Terminada");
        assign(responsable, soloSuya, "Solo suya", companero.id());
        assign(responsable, compartida, "Compartida", companero.id(), responsable.id());
        assign(responsable, terminada, "Terminada", companero.id());

        mockMvc.perform(authorized(delete("/api/teams/{id}/members/{userId}", teamId, companero.id()), companero))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/tasks/{id}", soloSuya), responsable))
                .andExpect(jsonPath("$.assignees.length()").value(0));
        mockMvc.perform(authorized(get("/api/tasks/{id}", compartida), responsable))
                .andExpect(jsonPath("$.assignees.length()").value(1))
                .andExpect(jsonPath("$.assignees[0].id").value(responsable.id()));
        mockMvc.perform(authorized(get("/api/tasks/{id}", terminada), responsable))
                .andExpect(jsonPath("$.assignees.length()").value(1))
                .andExpect(jsonPath("$.assignees[0].id").value(companero.id()));

        // La terminada se sigue pudiendo editar sin quitarle a quien la hizo.
        mockMvc.perform(authorized(put("/api/tasks/{id}", terminada), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Terminada y revisada","assigneeIds":[%d]}
                                """.formatted(companero.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignees[0].id").value(companero.id()));

        // Pero ya no se le puede asignar nada nuevo.
        mockMvc.perform(authorized(put("/api/tasks/{id}", soloSuya), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Solo suya","assigneeIds":[%d]}
                                """.formatted(companero.id())))
                .andExpect(status().isConflict());

        mockMvc.perform(authorized(get("/api/projects/{id}/activity", workspace.id()), responsable))
                .andExpect(jsonPath("$.content[*].details",
                        hasItem(containsString("deja la tarea 'Solo suya' sin asignar"))));
    }

    private void assign(TestUser user, Long taskId, String title, Long... assigneeIds) throws Exception {
        mockMvc.perform(authorized(put("/api/tasks/{id}", taskId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","assigneeIds":%s}
                                """.formatted(title, Arrays.toString(assigneeIds))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Las indicaciones para el plan se guardan y se leen con el miembro")
    void planNotesSurviveAndTravelWithTheMember() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser companero = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo con fichas");
        Long teamId = workspace.teamId();
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
        Workspace workspace = createWorkspace(responsable, "Equipo con fichas");
        Long teamId = workspace.teamId();
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
        Workspace workspace = createWorkspace(responsable, "Equipo con fichas");
        Long teamId = workspace.teamId();

        mockMvc.perform(authorized(
                        patch("/api/teams/{id}/members/{userId}/notes", teamId, responsable.id()), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("planNotes", "a".repeat(601)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
