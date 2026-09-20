package com.tfg.kanban.comment;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentControllerIT extends AbstractApiIT {

    @Test
    @DisplayName("Un miembro comenta, edita su comentario y lo borra")
    void commentLifecycle() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo comentarios");
        addMember(responsable, teamId, miembro, Role.MIEMBRO);
        Long projectId = createProject(responsable, teamId, "Practica comentarios");
        Long boardId = createBoard(responsable, projectId, "Tablero comentarios");
        Long taskId = createTask(miembro, columnIds(responsable, boardId)[0], "Tarea comentada");

        Long commentId = json(mockMvc.perform(authorized(post("/api/tasks/{id}/comments", taskId), miembro)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"Voy por el apartado 2\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.author.id").value(miembro.id()))
                        .andExpect(jsonPath("$.edited").value(false))
                        .andReturn())
                .path("id").asLong();

        mockMvc.perform(authorized(get("/api/tasks/{id}/comments", taskId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(authorized(put("/api/comments/{id}", commentId), miembro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Corregido: voy por el apartado 3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edited").value(true));

        mockMvc.perform(authorized(delete("/api/comments/{id}", commentId), miembro))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Otro miembro no puede editar un comentario ajeno, pero el RESPONSABLE si puede borrarlo")
    void onlyAuthorOrResponsableCanModify() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser autor = registerUser(Role.MIEMBRO);
        TestUser otro = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo moderacion");
        addMember(responsable, teamId, autor, Role.MIEMBRO);
        addMember(responsable, teamId, otro, Role.MIEMBRO);
        Long projectId = createProject(responsable, teamId, "Practica moderacion");
        Long boardId = createBoard(responsable, projectId, "Tablero moderacion");
        Long taskId = createTask(autor, columnIds(responsable, boardId)[0], "Tarea moderada");

        Long commentId = json(mockMvc.perform(authorized(post("/api/tasks/{id}/comments", taskId), autor)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"Comentario original\"}"))
                        .andExpect(status().isCreated())
                        .andReturn())
                .path("id").asLong();

        mockMvc.perform(authorized(put("/api/comments/{id}", commentId), otro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Te lo edito yo\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(delete("/api/comments/{id}", commentId), responsable))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Un DOCENTE lee los comentarios pero no puede escribir")
    void observerCannotComment() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser observador = registerUser(Role.DOCENTE);
        Long teamId = createTeam(responsable, "Equipo lectura");
        addMember(responsable, teamId, observador, Role.DOCENTE);
        Long projectId = createProject(responsable, teamId, "Practica lectura");
        Long boardId = createBoard(responsable, projectId, "Tablero lectura");
        Long taskId = createTask(responsable, columnIds(responsable, boardId)[0], "Tarea leida");

        mockMvc.perform(authorized(get("/api/tasks/{id}/comments", taskId), observador))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(post("/api/tasks/{id}/comments", taskId), observador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"No deberia poder comentar\"}"))
                .andExpect(status().isForbidden());
    }
}
