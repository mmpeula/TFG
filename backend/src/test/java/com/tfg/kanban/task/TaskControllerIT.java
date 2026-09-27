package com.tfg.kanban.task;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerIT extends AbstractApiIT {

    @Test
    @DisplayName("Un MIEMBRO crea, edita y borra tareas")
    void memberManagesTasks() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo tareas");
        Long teamId = workspace.teamId();
        addMember(responsable, teamId, miembro, Role.MIEMBRO);
        Long boardId = workspace.boardId();
        long[] columns = columnIds(responsable, boardId);

        Long taskId = createTask(miembro, columns[0], "Escribir la introduccion");

        mockMvc.perform(authorized(put("/api/tasks/{id}", taskId), miembro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Escribir la introduccion y el resumen","priority":"ALTA",
                                 "assigneeIds":[%d],"estimatedHours":3.5}
                                """.formatted(miembro.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("ALTA"))
                .andExpect(jsonPath("$.assignees[0].id").value(miembro.id()));

        mockMvc.perform(authorized(delete("/api/tasks/{id}", taskId), miembro))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Mover una tarea de columna la coloca en la posicion pedida y reindexa el origen")
    void moveTaskBetweenColumns() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo drag and drop");
        Long teamId = workspace.teamId();
        Long boardId = workspace.boardId();
        long[] columns = columnIds(responsable, boardId);

        Long first = createTask(responsable, columns[0], "Tarea 1");
        Long second = createTask(responsable, columns[0], "Tarea 2");
        Long third = createTask(responsable, columns[0], "Tarea 3");

        mockMvc.perform(authorized(patch("/api/tasks/{id}/move", first), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnId":%d,"position":0}
                                """.formatted(columns[1])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columnId").value(columns[1]))
                .andExpect(jsonPath("$.position").value(0));

        // La columna de origen queda reindexada 0,1 sin huecos
        mockMvc.perform(authorized(get("/api/boards/{id}", boardId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].tasks.length()").value(2))
                .andExpect(jsonPath("$.columns[0].tasks[0].id").value(second))
                .andExpect(jsonPath("$.columns[0].tasks[0].position").value(0))
                .andExpect(jsonPath("$.columns[0].tasks[1].id").value(third))
                .andExpect(jsonPath("$.columns[0].tasks[1].position").value(1))
                .andExpect(jsonPath("$.columns[1].tasks.length()").value(1));
    }

    @Test
    @DisplayName("El limite WIP de la columna destino bloquea el movimiento")
    void wipLimitBlocksMove() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo WIP");
        Long teamId = workspace.teamId();
        Long boardId = workspace.boardId();
        long[] columns = columnIds(responsable, boardId);

        // La columna "En curso" admite una sola tarea
        mockMvc.perform(authorized(put("/api/columns/{id}", columns[1]), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"En curso\",\"wipLimit\":1}"))
                .andExpect(status().isOk());

        Long first = createTask(responsable, columns[0], "Tarea A");
        Long second = createTask(responsable, columns[0], "Tarea B");

        mockMvc.perform(authorized(patch("/api/tasks/{id}/move", first), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnId":%d,"position":0}
                                """.formatted(columns[1])))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(patch("/api/tasks/{id}/move", second), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnId":%d,"position":0}
                                """.formatted(columns[1])))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Un DOCENTE puede leer el tablero pero no tocar las tareas")
    void observerIsReadOnly() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser observador = registerUser(Role.DOCENTE);
        Workspace workspace = createWorkspace(responsable, "Equipo observado");
        Long teamId = workspace.teamId();
        addMember(responsable, teamId, observador, Role.DOCENTE);
        Long boardId = workspace.boardId();
        long[] columns = columnIds(responsable, boardId);
        Long taskId = createTask(responsable, columns[0], "Tarea visible");

        mockMvc.perform(authorized(get("/api/boards/{id}", boardId), observador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("DOCENTE"));

        mockMvc.perform(authorized(post("/api/columns/{id}/tasks", columns[0]), observador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No deberia poder\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(patch("/api/tasks/{id}/move", taskId), observador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnId":%d,"position":0}
                                """.formatted(columns[1])))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(delete("/api/tasks/{id}", taskId), observador))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("No se puede asignar una tarea a alguien de fuera del equipo")
    void cannotAssignOutsider() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser extrano = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo asignaciones");
        Long teamId = workspace.teamId();
        Long boardId = workspace.boardId();
        long[] columns = columnIds(responsable, boardId);
        Long taskId = createTask(responsable, columns[0], "Tarea a asignar");

        mockMvc.perform(authorized(put("/api/tasks/{id}", taskId), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Tarea a asignar","assigneeIds":[%d]}
                                """.formatted(extrano.id())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("El buscador combina los filtros de tablero, columna y prioridad")
    void searchCombinesFilters() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo filtros");
        Long teamId = workspace.teamId();
        Long boardId = workspace.boardId();
        long[] columns = columnIds(responsable, boardId);

        Long urgente = createTask(responsable, columns[0], "Tarea urgente");
        createTask(responsable, columns[1], "Tarea normal");

        mockMvc.perform(authorized(put("/api/tasks/{id}", urgente), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tarea urgente\",\"priority\":\"CRITICA\"}"))
                .andExpect(status().isOk());

        // Por tablero: las dos tareas
        mockMvc.perform(authorized(get("/api/tasks"), responsable)
                        .param("boardId", String.valueOf(boardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        // Por columna: solo la primera
        mockMvc.perform(authorized(get("/api/tasks"), responsable)
                        .param("columnId", String.valueOf(columns[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(urgente));

        // Por prioridad, combinada con el tablero
        mockMvc.perform(authorized(get("/api/tasks"), responsable)
                        .param("boardId", String.valueOf(boardId))
                        .param("priority", "CRITICA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Tarea urgente"));

        // Sin filtros: al menos las dos de este proyecto, y ninguna de otros equipos
        mockMvc.perform(authorized(get("/api/tasks"), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("El buscador de tareas filtra por proyecto y por persona asignada")
    void searchFiltersTasks() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo busqueda");
        Long teamId = workspace.teamId();
        Long projectId = workspace.id();
        Long boardId = workspace.boardId();
        long[] columns = columnIds(responsable, boardId);
        Long taskId = createTask(responsable, columns[0], "Tarea buscada");

        mockMvc.perform(authorized(put("/api/tasks/{id}", taskId), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Tarea buscada","assigneeIds":[%d]}
                                """.formatted(responsable.id())))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(get("/api/tasks"), responsable)
                        .param("projectId", projectId.toString())
                        .param("assigneeId", responsable.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(taskId));
    }
}
