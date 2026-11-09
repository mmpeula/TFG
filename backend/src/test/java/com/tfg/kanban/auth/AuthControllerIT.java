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
    @DisplayName("Se puede elegir Alumno o Docente al registrarse")
    void canChooseAlumnoOrDocenteAtRegistration() throws Exception {
        String docente = "docente-" + UUID.randomUUID() + "@uni.es";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Profesor Soto","role":"DOCENTE"}
                                """.formatted(docente)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("DOCENTE"));

        String alumno = "alumno-" + UUID.randomUUID() + "@uni.es";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Ana Garcia","role":"MIEMBRO"}
                                """.formatted(alumno)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("MIEMBRO"));
    }

    @Test
    @DisplayName("Quien se registra no puede convertirse en ADMIN ni en RESPONSABLE global")
    void registrationCannotSelfAssignAdminOrResponsable() throws Exception {
        // ADMIN administra la plataforma entera: si se pudiera pedir aqui, darse
        // de alta bastaria para tener esa llave.
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin-%s@uni.es","password":"password123","fullName":"Atacante",
                                 "role":"ADMIN"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        // RESPONSABLE es un rol de equipo, no de cuenta: pedirlo aqui no significa
        // nada, y aceptarlo en silencio (como antes) escondia el error en vez de
        // avisar de que la peticion no tiene sentido.
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"resp-%s@uni.es","password":"password123","fullName":"Atacante",
                                 "role":"RESPONSABLE"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("El token de un DOCENTE recien registrado sigue sin abrir los endpoints de administracion")
    void aFreshlyRegisteredTeacherHasNoAdminPowers() throws Exception {
        String email = "docente-sin-poder-" + UUID.randomUUID() + "@uni.es";
        String token = json(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"%s","password":"password123","fullName":"Profesor Ruiz",
                                         "role":"DOCENTE"}
                                        """.formatted(email)))
                        .andExpect(status().isCreated())
                        .andReturn())
                .path("token").asText();

        TestUser victima = registerUser(Role.MIEMBRO);

        mockMvc.perform(patch("/api/users/{id}/role", victima.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"DOCENTE\"}"))
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
    @DisplayName("Una contrasena de mas de 72 bytes se rechaza con 400, aunque tenga menos de 72 caracteres")
    void rejectsPasswordsLongerThanBcryptAllows() throws Exception {
        // 40 eñes: 40 caracteres, pero 80 bytes. BCrypt no admite mas de 72 y antes esto era un 500.
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"eñes-%s@uni.es","password":"%s","fullName":"Larga"}
                                """.formatted(java.util.UUID.randomUUID(), "ñ".repeat(40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    @DisplayName("Sin token, /me responde 401")
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
