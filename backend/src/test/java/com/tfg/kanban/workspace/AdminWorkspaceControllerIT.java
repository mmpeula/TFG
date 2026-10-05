package com.tfg.kanban.workspace;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El panel de soporte: lo que un administrador ve de un workspace ajeno y, sobre
 * todo, lo que sigue sin poder hacer con el.
 */
class AdminWorkspaceControllerIT extends AbstractApiIT {

    @Test
    @DisplayName("El administrador ve todos los workspaces, tambien los que no son suyos")
    void theAdminSeesEveryWorkspace() throws Exception {
        TestUser duena = registerUser(Role.MIEMBRO);
        Long workspaceId = createWorkspace(duena, "Practica ajena").id();
        TestUser admin = registerUser(Role.ADMIN);

        mockMvc.perform(authorized(get("/api/admin/workspaces"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].name".formatted(workspaceId)).value("Practica ajena"))
                .andExpect(jsonPath("$[?(@.id == %d)].members[0].email".formatted(workspaceId))
                        .value(duena.email()));
    }

    @Test
    @DisplayName("Quien no es administrador no entra en el panel de soporte")
    void onlyAdminsGetIn() throws Exception {
        TestUser cualquiera = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(get("/api/admin/workspaces"), cualquiera))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Ver un workspace desde soporte no da acceso a su tablero ni a sus tareas")
    void supportDoesNotOpenTheBoard() throws Exception {
        TestUser duena = registerUser(Role.MIEMBRO);
        Long workspaceId = createWorkspace(duena, "Equipo privado").id();
        TestUser admin = registerUser(Role.ADMIN);

        // Sale en el panel de soporte...
        mockMvc.perform(authorized(get("/api/admin/workspaces"), admin))
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(workspaceId)).exists());

        // ...pero el contenido sigue siendo del equipo, y el admin no pertenece a el.
        mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(authorized(get("/api/projects/{id}/boards", workspaceId), admin))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un workspace sin responsable activo sale marcado como bloqueado y soporte lo desatasca")
    void supportUnblocksATeamWithoutResponsable() throws Exception {
        TestUser duena = registerUser(Role.MIEMBRO);
        TestUser companero = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(duena, "Equipo atascado");
        Long teamId = workspace.teamId();
        Long workspaceId = workspace.id();
        addMember(duena, teamId, companero, Role.MIEMBRO);

        TestUser admin = registerUser(Role.ADMIN);
        // Al deshabilitar a la unica responsable, el equipo se queda sin nadie que pueda administrarlo.
        mockMvc.perform(authorized(delete("/api/users/{id}", duena.id()), admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/admin/workspaces"), admin))
                .andExpect(jsonPath("$[?(@.id == %d)].blocked".formatted(workspaceId)).value(true));

        mockMvc.perform(authorized(
                        put("/api/admin/workspaces/{id}/members/{userId}/role", workspaceId, companero.id()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"RESPONSABLE\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/admin/workspaces"), admin))
                .andExpect(jsonPath("$[?(@.id == %d)].blocked".formatted(workspaceId)).value(false));

        // Y el companero ya puede administrarlo de verdad.
        mockMvc.perform(authorized(post("/api/teams/{id}/invitations", teamId), companero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"role\":\"MIEMBRO\"}".formatted(admin.email())))
                .andExpect(status().isConflict());  // ADMIN no puede estar en un equipo, pero paso el permiso
    }

    @Test
    @DisplayName("Soporte no puede meter en un equipo a quien no pertenece: no es una puerta de entrada")
    void supportCannotAddOutsiders() throws Exception {
        TestUser duena = registerUser(Role.MIEMBRO);
        TestUser extrano = registerUser(Role.MIEMBRO);
        Long workspaceId = createWorkspace(duena, "Equipo cerrado").id();
        TestUser admin = registerUser(Role.ADMIN);

        mockMvc.perform(authorized(
                        put("/api/admin/workspaces/{id}/members/{userId}/role", workspaceId, extrano.id()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"RESPONSABLE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("no está en el equipo")));
    }

    @Test
    @DisplayName("Soporte puede borrar un workspace al que se ha invitado a gente")
    void supportCanDeleteAWorkspaceWithInvitations() throws Exception {
        TestUser duena = registerUser(Role.MIEMBRO);
        TestUser invitada = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(duena, "Equipo con invitaciones");
        addMember(duena, workspace.teamId(), invitada, Role.MIEMBRO);
        TestUser admin = registerUser(Role.ADMIN);

        mockMvc.perform(authorized(delete("/api/admin/workspaces/{id}", workspace.id()), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Soporte puede borrar un workspace ajeno")
    void supportCanDeleteAWorkspace() throws Exception {
        TestUser duena = registerUser(Role.MIEMBRO);
        Long workspaceId = createWorkspace(duena, "Equipo a borrar").id();
        TestUser admin = registerUser(Role.ADMIN);

        mockMvc.perform(authorized(delete("/api/admin/workspaces/{id}", workspaceId), admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/workspaces"), duena))
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(workspaceId)).doesNotExist());
    }

    @Test
    @DisplayName("Suspender un workspace deja al equipo sin poder abrirlo, pero sin perder nada")
    void suspendingFreezesTheWorkspace() throws Exception {
        TestUser duena = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(duena, "Equipo que se para");
        Long teamId = workspace.teamId();
        Long workspaceId = workspace.id();
        TestUser admin = registerUser(Role.ADMIN);

        // Antes de suspenderlo, su equipo trabaja con normalidad.
        mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), duena))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(put("/api/admin/workspaces/{id}/enabled", workspaceId), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isNoContent());

        // Ya no puede abrirlo, ni tocar su equipo...
        mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), duena))
                .andExpect(status().isForbidden());
        mockMvc.perform(authorized(get("/api/projects/{id}/boards", workspaceId), duena))
                .andExpect(status().isForbidden());
        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), duena))
                .andExpect(status().isForbidden());

        // ...pero sigue en su lista, marcado como suspendido: no ha desaparecido.
        mockMvc.perform(authorized(get("/api/workspaces"), duena))
                .andExpect(jsonPath("$[?(@.id == %d)].enabled".formatted(workspaceId)).value(false));

        // Y al reactivarlo todo vuelve a estar donde estaba.
        mockMvc.perform(authorized(put("/api/admin/workspaces/{id}/enabled", workspaceId), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), duena))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Soporte cambia cualquier rol, no solo asciende a responsable")
    void supportChangesAnyRole() throws Exception {
        TestUser duena = registerUser(Role.MIEMBRO);
        TestUser companero = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(duena, "Equipo con dos responsables");
        Long teamId = workspace.teamId();
        Long workspaceId = workspace.id();
        addMember(duena, teamId, companero, Role.RESPONSABLE);
        TestUser admin = registerUser(Role.ADMIN);

        // Degradar: lo contrario de ascender, y tambien vale.
        mockMvc.perform(authorized(
                        put("/api/admin/workspaces/{id}/members/{userId}/role", workspaceId, companero.id()), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MIEMBRO\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/teams/{id}/members", teamId), duena))
                .andExpect(jsonPath("$[?(@.user.id == %d)].role".formatted(companero.id())).value("MIEMBRO"));
    }

    @Test
    @DisplayName("Ni desde soporte se deja al equipo sin ninguna persona responsable")
    void supportCannotLeaveTheTeamWithoutResponsable() throws Exception {
        TestUser duena = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(duena, "Equipo de una sola responsable");
        Long teamId = workspace.teamId();
        Long workspaceId = workspace.id();
        TestUser admin = registerUser(Role.ADMIN);

        mockMvc.perform(authorized(
                        put("/api/admin/workspaces/{id}/members/{userId}/role", workspaceId, duena.id()), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MIEMBRO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("sin responsable")));
    }

    @Test
    @DisplayName("Soporte sigue trabajando sobre un workspace suspendido: si no, no habria forma de arreglarlo")
    void supportStillWorksOnASuspendedWorkspace() throws Exception {
        TestUser duena = registerUser(Role.MIEMBRO);
        TestUser companero = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(duena, "Equipo suspendido");
        Long teamId = workspace.teamId();
        Long workspaceId = workspace.id();
        addMember(duena, teamId, companero, Role.MIEMBRO);
        TestUser admin = registerUser(Role.ADMIN);

        mockMvc.perform(authorized(put("/api/admin/workspaces/{id}/enabled", workspaceId), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/admin/workspaces"), admin))
                .andExpect(jsonPath("$[?(@.id == %d)].enabled".formatted(workspaceId)).value(false));
        mockMvc.perform(authorized(
                        put("/api/admin/workspaces/{id}/members/{userId}/role", workspaceId, companero.id()), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"RESPONSABLE\"}"))
                .andExpect(status().isNoContent());
    }
}
