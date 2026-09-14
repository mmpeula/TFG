package com.tfg.kanban.common;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de errores de la API: toda respuesta de error, venga del filtro de
 * seguridad o del {@link GlobalExceptionHandler}, tiene que ser un {@link ApiError}
 * con el mismo formato.
 *
 * <p>Estos casos cubren caminos que los tests por recurso no tocaban: el 401 que
 * emite {@link RestAuthenticationEntryPoint} (los 401 de {@code /api/auth/**} no
 * sirven, porque esa ruta es publica y su error lo produce el controlador) y el 404
 * de un endpoint inexistente.</p>
 */
class ErrorHandlingIT extends AbstractApiIT {

    @Test
    @DisplayName("Un endpoint protegido sin token responde 401 con el ApiError estandar")
    void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/teams"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Un token invalido tampoco da acceso: 401")
    void invalidTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/teams").header("Authorization", "Bearer no-es-un-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Una ruta que no existe responde 404, no 500")
    void unknownEndpointReturnsNotFound() throws Exception {
        TestUser user = registerUser(Role.RESPONSABLE);

        mockMvc.perform(authorized(get("/api/no-existe"), user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Un cuerpo JSON ilegible responde 400 MALFORMED_REQUEST")
    void malformedJsonReturnsBadRequest() throws Exception {
        TestUser user = registerUser(Role.RESPONSABLE);

        mockMvc.perform(authorized(post("/api/teams"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("Un parametro con el tipo equivocado responde 400 MALFORMED_REQUEST")
    void parameterTypeMismatchReturnsBadRequest() throws Exception {
        TestUser user = registerUser(Role.RESPONSABLE);

        mockMvc.perform(authorized(get("/api/tasks"), user).param("projectId", "no-es-un-numero"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
