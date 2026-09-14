package com.tfg.kanban.auth;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AbstractApiIT {

    @Test
    @DisplayName("Registro, login y /me completan el ciclo de autenticacion")
    void registerLoginAndMe() throws Exception {
        String email = "auth-" + UUID.randomUUID() + "@uni.es";

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Ana Garcia"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("MIEMBRO"));

        String token = json(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"%s","password":"password123"}
                                        """.formatted(email)))
                        .andExpect(status().isOk())
                        .andReturn())
                .path("token").asText();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Ana Garcia"));
    }

    @Test
    @DisplayName("Quien se registra no puede elegir su rol global: siempre entra como MIEMBRO")
    void registrationCannotSelfAssignGlobalRole() throws Exception {
        String email = "escalada-" + UUID.randomUUID() + "@uni.es";

        String token = json(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"%s","password":"password123","fullName":"Atacante",
                                         "role":"RESPONSABLE"}
                                        """.formatted(email)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.user.role").value("MIEMBRO"))
                        .andReturn())
                .path("token").asText();

        // Y el token que se le entrega tampoco abre los endpoints reservados al
        // RESPONSABLE global: sin esto, cualquiera podria cambiar el rol o la
        // contrasena de otra cuenta con solo darse de alta.
        TestUser victima = registerUser(Role.MIEMBRO);

        mockMvc.perform(patch("/api/users/{id}/role", victima.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OBSERVADOR\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/{id}", victima.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Secuestrada","password":"contrasenaDelAtacante1"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/users/{id}", victima.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("No se puede registrar dos veces el mismo email")
    void rejectsDuplicatedEmail() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Otro"}
                                """.formatted(user.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("La contrasena incorrecta devuelve 401 con el error estandar")
    void rejectsWrongPassword() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"otraClaveDistinta"}
                                """.formatted(user.email())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("El registro valida los campos de entrada")
    void validatesRegistrationPayload() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-es-un-email","password":"corta","fullName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("Sin token, /me responde 401")
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
