package com.tfg.kanban.user;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIT extends AbstractApiIT {

    @Test
    @DisplayName("La busqueda de usuarios encuentra por email y nunca devuelve el hash de la contrasena")
    void searchFindsUserAndHidesPassword() throws Exception {
        TestUser admin = registerUser(Role.ADMIN);
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(get("/api/users").param("search", user.email()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value(user.email()))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("El directorio de cuentas es del administrador: nadie mas lo lista")
    void onlyAdminBrowsesTheDirectory() throws Exception {
        // Es la agenda de toda la plataforma (nombres y emails de gente con la que
        // no se comparte nada). Para invitar a alguien a un equipo no hace falta:
        // se le invita por su email, que quien invita ya conoce.
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(get("/api/users"), user))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/{id} devuelve la ficha propia y 404 si no existe")
    void findByIdAndNotFound() throws Exception {
        TestUser admin = registerUser(Role.ADMIN);
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(get("/api/users/{id}", user.id()), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id()));

        mockMvc.perform(authorized(get("/api/users/{id}", 999999), admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("La ficha de otra persona no se lee, ni siquiera siendo de su equipo")
    void cannotReadSomeoneElsesAccount() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);
        TestUser other = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(get("/api/users/{id}", other.id()), user))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cada uno edita su perfil, pero no el de otro")
    void updateOwnProfileOnly() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);
        TestUser other = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(put("/api/users/{id}", user.id()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nombre Actualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nombre Actualizado"));

        mockMvc.perform(authorized(put("/api/users/{id}", user.id()), other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Te lo cambio yo\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cambiar la contrasena permite iniciar sesion con la nueva")
    void passwordChangeTakesEffect() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(put("/api/users/{id}", user.id()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Usuario de prueba","currentPassword":"password123",\
                                "password":"nuevaClave456"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"nuevaClave456"}
                                """.formatted(user.email())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cambiarse la contrasena exige saber la actual")
    void changingOwnPasswordNeedsTheCurrentOne() throws Exception {
        // Una sesion abierta y sin vigilancia no puede bastar para echar a alguien
        // de su propia cuenta: quien se sentara delante elegiria la contrasena
        // nueva y el dueno ya no la sabria.
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(put("/api/users/{id}", user.id()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Usuario de prueba\",\"password\":\"nuevaClave456\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        mockMvc.perform(authorized(put("/api/users/{id}", user.id()), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Usuario de prueba","currentPassword":"noEsLaSuya",\
                                "password":"nuevaClave456"}
                                """))
                .andExpect(status().isConflict());

        // Y la de siempre sigue valiendo: no se ha cambiado nada.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(user.email())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El administrador restablece la contrasena de otro sin saber la suya")
    void adminResetsSomeoneElsesPassword() throws Exception {
        TestUser admin = registerUser(Role.ADMIN);
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(put("/api/users/{id}", user.id()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Usuario de prueba\",\"password\":\"claveRestablecida9\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"claveRestablecida9"}
                                """.formatted(user.email())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El rol global solo lo cambia un ADMIN")
    void onlyAdminChangesGlobalRoles() throws Exception {
        TestUser admin = registerUser(Role.ADMIN);
        TestUser member = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(patch("/api/users/{id}/role", admin.id()), member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MIEMBRO\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(patch("/api/users/{id}/role", member.id()), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"DOCENTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DOCENTE"));
    }

    @Test
    @DisplayName("Ser RESPONSABLE de un equipo no da permisos de administrador de la plataforma")
    void teamResponsableIsNotPlatformAdmin() throws Exception {
        // RNF-02: el rol que cuenta es el del equipo dueno del recurso, y las
        // cuentas de usuario no son de ningun equipo. Ser el RESPONSABLE de un
        // equipo (o de todos los equipos en los que se participa) no debe abrir
        // la gestion de cuentas de otras personas de la plataforma.
        TestUser teamLeader = registerUser(Role.MIEMBRO);
        TestUser other = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(patch("/api/users/{id}/role", other.id()), teamLeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"DOCENTE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(delete("/api/users/{id}", other.id()), teamLeader))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN no se puede asignar como rol de un equipo")
    void adminCannotBeATeamRole() throws Exception {
        TestUser owner = registerUser(Role.MIEMBRO);
        TestUser invitee = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(owner, "Equipo de prueba");

        mockMvc.perform(authorized(post("/api/teams/{id}/members", teamId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"role\":\"ADMIN\"}".formatted(invitee.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Dar de baja una cuenta la deshabilita; nadie puede darse de baja a si mismo")
    void disableAccount() throws Exception {
        TestUser admin = registerUser(Role.ADMIN);
        TestUser victim = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(delete("/api/users/{id}", admin.id()), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        mockMvc.perform(authorized(delete("/api/users/{id}", victim.id()), admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/users/{id}", victim.id()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Una cuenta deshabilitada ya no puede iniciar sesion
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(victim.email())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Una cuenta reactivada puede volver a iniciar sesion")
    void enableAccount() throws Exception {
        TestUser admin = registerUser(Role.ADMIN);
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(delete("/api/users/{id}", user.id()), admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(authorized(patch("/api/users/{id}/enable", user.id()), admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(user.email())))
                .andExpect(status().isOk());
    }

    // La regla de "nunca sin administradores" (UserService.ensureNotLastAdmin) se
    // prueba en UserServiceTest con un repositorio simulado: el Oracle de estos
    // tests de integracion es compartido con el resto de clases y siempre tiene
    // al menos el administrador que crea AdminBootstrapRunner al arrancar, asi
    // que "queda un unico ADMIN en toda la base de datos" no es reproducible aqui.
}
