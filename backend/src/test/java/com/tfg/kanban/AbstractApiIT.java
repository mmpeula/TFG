package com.tfg.kanban;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de integracion de la API.
 *
 * Levanta la aplicacion completa contra el Oracle de Testcontainers y ofrece
 * atajos para registrar usuarios, autenticarse y montar el arbol
 * equipo -> proyecto -> tablero -> columna -> tarea que necesitan casi todos.
 *
 * Cada test usa emails aleatorios y crea sus propios datos, asi que no hace
 * falta limpiar la base de datos entre pruebas.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractApiIT extends AbstractOracleContainerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    /** Usuario recien registrado junto con su token. */
    protected record TestUser(Long id, String email, String token) {

        public String bearer() {
            return "Bearer " + token;
        }
    }

    /**
     * Registra un usuario y le deja el rol global indicado.
     *
     * <p>El alta siempre crea un MIEMBRO: el rol global no se puede pedir al
     * registrarse (lo concede despues un RESPONSABLE global). Cuando el test
     * necesita otro rol se asigna en base de datos y se vuelve a iniciar sesion,
     * porque las autoridades salen del token y no de la fila de la tabla.</p>
     */
    protected TestUser registerUser(Role role) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@uni.es";
        String body = """
                {"email":"%s","password":"password123","fullName":"Usuario de prueba"}
                """.formatted(email);

        JsonNode response = json(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn());

        Long id = response.path("user").path("id").asLong();
        String token = response.path("token").asText();

        if (role != Role.MIEMBRO) {
            token = promoteAndLogin(id, email, role);
        }
        return new TestUser(id, email, token);
    }

    /** Cambia el rol global directamente y devuelve un token nuevo que ya lo lleva. */
    private String promoteAndLogin(Long userId, String email, Role role) throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        user.setRole(role);
        userRepository.saveAndFlush(user);

        return json(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()).path("token").asText();
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder, TestUser user) {
        return builder.header(HttpHeaders.AUTHORIZATION, user.bearer());
    }

    // ---------------------------------------------------------- atajos de dominio

    protected Long createTeam(TestUser owner, String name) throws Exception {
        return json(mockMvc.perform(authorized(post("/api/teams"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Equipo de prueba"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asLong();
    }

    protected void addMember(TestUser responsable, Long teamId, TestUser member, Role role) throws Exception {
        mockMvc.perform(authorized(post("/api/teams/{id}/members", teamId), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","role":"%s"}
                                """.formatted(member.email(), role.name())))
                .andExpect(status().isCreated());
    }

    protected Long createProject(TestUser responsable, Long teamId, String name) throws Exception {
        return json(mockMvc.perform(authorized(post("/api/projects"), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamId":%d,"name":"%s","description":"Practica de prueba",
                                 "briefing":"Guion de la practica","deadline":"2026-12-15"}
                                """.formatted(teamId, name)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asLong();
    }

    protected Long createBoard(TestUser responsable, Long projectId, String name) throws Exception {
        return json(mockMvc.perform(authorized(post("/api/projects/{id}/boards", projectId), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Tablero de prueba"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asLong();
    }

    /** Devuelve los ids de las columnas por defecto del tablero, en orden. */
    protected long[] columnIds(TestUser user, Long boardId) throws Exception {
        JsonNode columns = json(mockMvc.perform(authorized(get("/api/boards/{id}", boardId), user))
                .andExpect(status().isOk())
                .andReturn()).path("columns");

        long[] ids = new long[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            ids[i] = columns.get(i).path("id").asLong();
        }
        return ids;
    }

    protected Long createTask(TestUser user, Long columnId, String title) throws Exception {
        return json(mockMvc.perform(authorized(post("/api/columns/{id}/tasks", columnId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"Descripcion","priority":"MEDIA"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asLong();
    }
}
