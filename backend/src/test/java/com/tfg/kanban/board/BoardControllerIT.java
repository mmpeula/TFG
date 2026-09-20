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
        Long teamId = createTeam(responsable, "Equipo tablero");
        Long projectId = createProject(responsable, teamId, "Practica tablero");
        Long boardId = createBoard(responsable, projectId, "Tablero principal");

        mockMvc.perform(authorized(get("/api/boards/{id}", boardId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.columns[0].name").value("Por hacer"))
                .andExpect(jsonPath("$.columns[0].position").value(0))
                .andExpect(jsonPath("$.columns[2].name").value("Hecho"));
    }

    @Test
    @DisplayName("Solo el RESPONSABLE crea, reordena y borra columnas")
    void columnManagementIsRestricted() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo columnas");
        addMember(responsable, teamId, miembro, Role.MIEMBRO);
        Long projectId = createProject(responsable, teamId, "Practica columnas");
        Long boardId = createBoard(responsable, projectId, "Tablero columnas");

        mockMvc.perform(authorized(post("/api/boards/{id}/columns", boardId), miembro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bloqueado\"}"))
                .andExpect(status().isForbidden());

        Long columnId = json(mockMvc.perform(authorized(post("/api/boards/{id}/columns", boardId), responsable)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Revision\",\"wipLimit\":2}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.position").value(3))
                        .andReturn())
                .path("id").asLong();

        // Reordenar la nueva columna a la primera posicion
        mockMvc.perform(authorized(patch("/api/columns/{id}/move", columnId), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(columnId))
                .andExpect(jsonPath("$[0].position").value(0));

        mockMvc.perform(authorized(delete("/api/columns/{id}", columnId), responsable))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("No se borra una columna que tiene tareas")
    void cannotDeleteColumnWithTasks() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo columna llena");
        Long projectId = createProject(responsable, teamId, "Practica columna llena");
        Long boardId = createBoard(responsable, projectId, "Tablero");
        long[] columns = columnIds(responsable, boardId);
        createTask(responsable, columns[0], "Tarea que estorba");

        mockMvc.perform(authorized(delete("/api/columns/{id}", columns[0]), responsable))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("El listado de tableros de un proyecto trae el rol propio y no las columnas")
    void listsBoardsOfProject() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        TestUser ajeno = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo listado");
        addMember(responsable, teamId, miembro, Role.MIEMBRO);
        Long projectId = createProject(responsable, teamId, "Practica listado");
        createBoard(responsable, projectId, "Tablero uno");
        createBoard(responsable, projectId, "Tablero dos");

        // El resumen no carga las columnas: es la llamada barata de la pantalla de proyecto.
        mockMvc.perform(authorized(get("/api/projects/{id}/boards", projectId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Tablero uno"))
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

    @Test
    @DisplayName("Solo el RESPONSABLE edita el tablero")
    void onlyResponsableUpdatesBoard() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        TestUser observador = registerUser(Role.DOCENTE);
        Long teamId = createTeam(responsable, "Equipo edicion");
        addMember(responsable, teamId, miembro, Role.MIEMBRO);
        addMember(responsable, teamId, observador, Role.DOCENTE);
        Long projectId = createProject(responsable, teamId, "Practica edicion");
        Long boardId = createBoard(responsable, projectId, "Nombre viejo");

        String body = "{\"name\":\"Nombre nuevo\",\"description\":\"Descripcion nueva\"}";

        mockMvc.perform(authorized(put("/api/boards/{id}", boardId), miembro)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(authorized(put("/api/boards/{id}", boardId), observador)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(put("/api/boards/{id}", boardId), responsable)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre nuevo"))
                .andExpect(jsonPath("$.description").value("Descripcion nueva"));

        // Un nombre en blanco no es un nombre.
        mockMvc.perform(authorized(put("/api/boards/{id}", boardId), responsable)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Solo el RESPONSABLE borra el tablero, y se lleva columnas y tareas")
    void onlyResponsableDeletesBoard() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo borrado");
        addMember(responsable, teamId, miembro, Role.MIEMBRO);
        Long projectId = createProject(responsable, teamId, "Practica borrado");
        Long boardId = createBoard(responsable, projectId, "Tablero a borrar");
        long[] columns = columnIds(responsable, boardId);
        Long taskId = createTask(miembro, columns[0], "Tarea que se va con el tablero");

        mockMvc.perform(authorized(delete("/api/boards/{id}", boardId), miembro))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(delete("/api/boards/{id}", boardId), responsable))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/boards/{id}", boardId), responsable))
                .andExpect(status().isForbidden());
        // La tarea desaparece con el tablero (cascada), no queda huerfana.
        mockMvc.perform(authorized(get("/api/tasks/{id}", taskId), responsable))
                .andExpect(status().isForbidden());
        mockMvc.perform(authorized(get("/api/projects/{id}/boards", projectId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Las columnas se listan ordenadas y con el numero de tareas")
    void listsColumnsWithTaskCount() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser ajeno = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo columnas listadas");
        Long projectId = createProject(responsable, teamId, "Practica columnas listadas");
        Long boardId = createBoard(responsable, projectId, "Tablero columnas listadas");
        long[] columns = columnIds(responsable, boardId);
        createTask(responsable, columns[0], "Primera");
        createTask(responsable, columns[0], "Segunda");
        createTask(responsable, columns[1], "Tercera");

        mockMvc.perform(authorized(get("/api/boards/{id}/columns", boardId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Por hacer"))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[0].taskCount").value(2))
                .andExpect(jsonPath("$[1].taskCount").value(1))
                .andExpect(jsonPath("$[2].taskCount").value(0))
                // El resumen no incluye las tareas, solo cuantas hay.
                .andExpect(jsonPath("$[0].tasks").doesNotExist());

        mockMvc.perform(authorized(get("/api/boards/{id}/columns", boardId), ajeno))
                .andExpect(status().isForbidden());
    }
}
