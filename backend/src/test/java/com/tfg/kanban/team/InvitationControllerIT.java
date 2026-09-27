package com.tfg.kanban.team;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El recorrido entero de una invitacion contra la base de datos de verdad: lo
 * que importa es que invitar no meta a nadie y que aceptar si.
 */
class InvitationControllerIT extends AbstractApiIT {

    private String invitar(com.tfg.kanban.AbstractApiIT.TestUser quien, Long teamId,
                           com.tfg.kanban.AbstractApiIT.TestUser aQuien, Role role) throws Exception {
        return json(mockMvc.perform(authorized(post("/api/teams/{id}/invitations", teamId), quien)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","role":"%s"}
                                """.formatted(aQuien.email(), role.name())))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();
    }

    @Test
    @DisplayName("Invitar deja a la persona pendiente; solo al aceptar entra en el equipo")
    void theInvitedOneJoinsOnlyAfterAccepting() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser invitado = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Grupo con invitaciones");
        Long teamId = workspace.teamId();

        invitar(responsable, teamId, invitado, Role.MIEMBRO);

        // Sigue habiendo una sola persona en el equipo: la invitacion no da acceso.
        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), responsable))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(authorized(get("/api/teams/{id}/invitations", teamId), responsable))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDIENTE"));

        // Y el invitado todavia no ve el equipo.
        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), invitado))
                .andExpect(status().isForbidden());

        String token = json(mockMvc.perform(authorized(get("/api/invitations/mine"), invitado))
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn()).path(0).path("token").asText();

        mockMvc.perform(authorized(post("/api/invitations/{token}/accept", token), invitado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MIEMBRO"));

        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), responsable))
                .andExpect(jsonPath("$.length()").value(2));
        // Ya respondida, deja de estar pendiente.
        mockMvc.perform(authorized(get("/api/teams/{id}/invitations", teamId), responsable))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Invitar a un email sin cuenta responde 404 explicando que no existe")
    void unknownEmailIsReported() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Grupo 4");
        Long teamId = workspace.teamId();

        mockMvc.perform(authorized(post("/api/teams/{id}/invitations", teamId), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@uni.es\",\"role\":\"MIEMBRO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No hay ninguna cuenta")));
    }

    @Test
    @DisplayName("Un MIEMBRO del equipo no puede invitar: eso es del responsable")
    void onlyTheResponsableCanInvite() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        TestUser otro = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Grupo 4");
        Long teamId = workspace.teamId();
        addMember(responsable, teamId, miembro, Role.MIEMBRO);

        mockMvc.perform(authorized(post("/api/teams/{id}/invitations", teamId), miembro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"role\":\"MIEMBRO\"}".formatted(otro.email())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Una invitacion ajena no se puede aceptar")
    void cannotAcceptSomeoneElsesInvitation() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser invitado = registerUser(Role.MIEMBRO);
        TestUser curioso = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Grupo 4");
        Long teamId = workspace.teamId();
        invitar(responsable, teamId, invitado, Role.MIEMBRO);

        String token = json(mockMvc.perform(authorized(get("/api/invitations/mine"), invitado)).andReturn())
                .path(0).path("token").asText();

        mockMvc.perform(authorized(post("/api/invitations/{token}/accept", token), curioso))
                .andExpect(status().isNotFound());
        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), responsable))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("El responsable puede cancelar una invitacion antes de que respondan")
    void theResponsableCanCancel() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser invitado = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Grupo 4");
        Long teamId = workspace.teamId();
        String id = invitar(responsable, teamId, invitado, Role.MIEMBRO);

        mockMvc.perform(authorized(delete("/api/teams/{id}/invitations/{inv}", teamId, id), responsable))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/invitations/mine"), invitado))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("No se invita dos veces a la misma persona mientras no responda")
    void cannotInviteTwiceWhilePending() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser invitado = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Grupo 4");
        Long teamId = workspace.teamId();
        invitar(responsable, teamId, invitado, Role.MIEMBRO);

        mockMvc.perform(authorized(post("/api/teams/{id}/invitations", teamId), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"role\":\"MIEMBRO\"}".formatted(invitado.email())))
                .andExpect(status().isConflict());
    }
}
