package com.tfg.kanban.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerIT extends AbstractApiIT {

    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private CompletedAtBackfill completedAtBackfill;
    @Autowired
    private TransactionTemplate transactions;

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
    @DisplayName("La descripcion tiene un tope de 10.000 caracteres")
    void descriptionHasALimit() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo descripciones");
        long[] columns = columnIds(responsable, workspace.boardId());

        mockMvc.perform(authorized(post("/api/columns/{id}/tasks", columns[0]), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Larga\",\"description\":\"%s\"}".formatted("a".repeat(10_001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("description"));

        mockMvc.perform(authorized(post("/api/columns/{id}/tasks", columns[0]), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Justa\",\"description\":\"%s\"}".formatted("a".repeat(10_000))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Mover una tarea de columna la coloca en la posicion pedida y reindexa el origen")
    void moveTaskBetweenColumns() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo drag and drop");
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
    @DisplayName("Mover una tarea no cambia la ultima modificacion de las que solo se desplazan")
    void movingATaskDoesNotTouchTheOthers() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo fechas");
        Long boardId = workspace.boardId();
        long[] columns = columnIds(responsable, boardId);

        Long first = createTask(responsable, columns[0], "Tarea 1");
        createTask(responsable, columns[0], "Tarea 2");
        createTask(responsable, columns[0], "Tarea 3");
        JsonNode antes = tasksOfFirstColumn(responsable, boardId);

        mockMvc.perform(authorized(patch("/api/tasks/{id}/move", first), responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"columnId":%d,"position":0}
                                """.formatted(columns[1])))
                .andExpect(status().isOk());

        JsonNode despues = tasksOfFirstColumn(responsable, boardId);
        // Las dos que quedan suben un puesto...
        assertThat(despues.get(0).get("position").asInt()).isZero();
        assertThat(despues.get(1).get("position").asInt()).isEqualTo(1);
        // ...pero nadie las ha tocado: para las metricas y los avisos siguen igual que antes.
        assertThat(despues.get(0).get("updatedAt").asText()).isEqualTo(antes.get(1).get("updatedAt").asText());
        assertThat(despues.get(1).get("updatedAt").asText()).isEqualTo(antes.get(2).get("updatedAt").asText());
    }

    private JsonNode tasksOfFirstColumn(TestUser user, Long boardId) throws Exception {
        return json(mockMvc.perform(authorized(get("/api/boards/{id}", boardId), user)).andReturn())
                .get("columns").get(0).get("tasks");
    }

    private JsonNode task(TestUser user, Long taskId) throws Exception {
        return json(mockMvc.perform(authorized(get("/api/tasks/{id}", taskId), user)).andReturn());
    }

    private void move(TestUser user, Long taskId, long columnId) throws Exception {
        mockMvc.perform(authorized(patch("/api/tasks/{id}/move", taskId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columnId\":%d,\"position\":0}".formatted(columnId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Al pasar a «Hecho» se apunta cuando se termino; al sacarla de ahi, se quita")
    void movingToDoneRecordsWhenItWasFinished() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo terminadas");
        long[] columns = columnIds(responsable, workspace.boardId());
        Long taskId = createTask(responsable, columns[0], "Escribir la memoria");
        assertThat(task(responsable, taskId).has("completedAt")).isFalse();

        Instant antes = Instant.now();
        move(responsable, taskId, columns[2]);
        Instant terminada = Instant.parse(task(responsable, taskId).get("completedAt").asText());
        assertThat(terminada).isBetween(antes.minusSeconds(5), Instant.now().plusSeconds(5));

        move(responsable, taskId, columns[1]);
        assertThat(task(responsable, taskId).has("completedAt")).isFalse();
    }

    @Test
    @DisplayName("A las que ya estaban terminadas sin fecha se les pone, sin cambiar su ultima modificacion")
    void backfillsTheTasksThatWereAlreadyDone() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo de antes");
        long[] columns = columnIds(responsable, workspace.boardId());
        Long taskId = createTask(responsable, columns[0], "Tarea de antes del campo");
        move(responsable, taskId, columns[2]);
        Instant terminada = Instant.parse(task(responsable, taskId).get("completedAt").asText());

        // Como estaban antes de existir el campo: en «Hecho», pero sin fecha.
        transactions.executeWithoutResult(tx -> taskRepository.updateCompletedAt(taskId, null));
        String ultimoCambio = task(responsable, taskId).get("updatedAt").asText();

        completedAtBackfill.run(null);

        JsonNode despues = task(responsable, taskId);
        // La fecha sale del historial («Movió la tarea … a 'Hecho'»), que se apunta en el mismo momento.
        assertThat(Instant.parse(despues.get("completedAt").asText()))
                .isBetween(terminada.minusSeconds(5), terminada.plusSeconds(5));
        assertThat(despues.get("updatedAt").asText()).isEqualTo(ultimoCambio);
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
