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
 * atajos para registrar usuarios, autenticarse, crear un workspace (equipo,
 * proyecto y tablero de una vez), invitar a gente y crear tareas.
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
     * registrarse (un ADMIN lo concede despues). Cuando el test necesita otro
     * rol se asigna en base de datos y se vuelve a iniciar sesion, porque las
     * autoridades salen del token y no de la fila de la tabla.</p>
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

    /**
     * Un workspace recien creado. Su id es tambien el de su proyecto: por debajo
     * son la misma fila, y las rutas de /api/projects lo reciben asi.
     */
    protected record Workspace(Long id, Long teamId, Long boardId) {
    }

    /** Equipo, proyecto y tablero de una vez, como los crea la aplicacion; quien lo crea queda RESPONSABLE. */
    protected Workspace createWorkspace(TestUser owner, String name) throws Exception {
        JsonNode workspace = json(mockMvc.perform(authorized(post("/api/workspaces"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Practica de prueba"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn());
        return new Workspace(workspace.path("id").asLong(), workspace.path("teamId").asLong(),
                workspace.path("boardId").asLong());
    }

    /**
     * Mete a alguien en el equipo como la aplicacion: invitacion y aceptacion. Entra
     * con el rol de su cuenta (alumno o docente); si se pide RESPONSABLE, se le
     * sube despues, que es tambien como se hace de verdad.
     */
    protected void addMember(TestUser responsable, Long teamId, TestUser member, Role role) throws Exception {
        mockMvc.perform(authorized(post("/api/teams/{id}/invitations", teamId), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(member.email())))
                .andExpect(status().isCreated());

        String token = json(mockMvc.perform(authorized(get("/api/invitations/mine"), member))
                .andExpect(status().isOk())
                .andReturn()).get(0).path("token").asText();
        mockMvc.perform(authorized(post("/api/invitations/{token}/accept", token), member))
                .andExpect(status().isOk());

        if (role == Role.RESPONSABLE) {
            mockMvc.perform(authorized(patch("/api/teams/{id}/members/{userId}", teamId, member.id()), responsable)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"RESPONSABLE\"}"))
                    .andExpect(status().isOk());
        }
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
