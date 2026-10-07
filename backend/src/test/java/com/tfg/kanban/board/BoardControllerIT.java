package com.tfg.kanban.board;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BoardControllerIT extends AbstractApiIT {

    @Test
    @DisplayName("El tablero nace con las tres columnas por defecto")
    void boardIsCreatedWithDefaultColumns() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo tablero");
        Long boardId = workspace.boardId();

        mockMvc.perform(authorized(get("/api/boards/{id}", boardId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.columns[0].name").value("Por hacer"))
                .andExpect(jsonPath("$.columns[0].position").value(0))
                .andExpect(jsonPath("$.columns[2].name").value("Hecho"));
    }

    @Test
    @DisplayName("Las tres columnas son fijas: ni el RESPONSABLE puede crear, editar ni borrar una")
    void columnsAreFixed() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo columnas");
        Long boardId = workspace.boardId();
        long[] columns = columnIds(responsable, boardId);

        mockMvc.perform(authorized(post("/api/boards/{id}/columns", boardId), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Revision\"}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(authorized(put("/api/columns/{id}", columns[1]), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Otra\"}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(authorized(delete("/api/columns/{id}", columns[2]), responsable))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(authorized(get("/api/boards/{id}", boardId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.columns[1].name").value("En progreso"))
                .andExpect(jsonPath("$.columns[2].name").value("Hecho"));
    }

    @Test
    @DisplayName("El listado de tableros de un proyecto trae el rol propio y no las columnas")
    void listsBoardsOfProject() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        TestUser ajeno = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo listado");
        Long teamId = workspace.teamId();
        addMember(responsable, teamId, miembro, Role.MIEMBRO);
        Long projectId = workspace.id();

        // El resumen no carga las columnas: es la llamada barata de la pantalla de proyecto.
        mockMvc.perform(authorized(get("/api/projects/{id}/boards", projectId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].projectId").value(projectId))
                .andExpect(jsonPath("$[0].myRole").value("RESPONSABLE"))
                .andExpect(jsonPath("$[0].columns").doesNotExist());

        // Cada usuario ve su propio rol en el equipo dueno del tablero.
        mockMvc.perform(authorized(get("/api/projects/{id}/boards", projectId), miembro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].myRole").value("MIEMBRO"));

        // Quien no pertenece al equipo no puede ni listar.
        mockMvc.perform(authorized(get("/api/projects/{id}/boards", projectId), ajeno))
                .andExpect(status().isForbidden());
    }
}
