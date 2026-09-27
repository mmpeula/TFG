package com.tfg.kanban.workspace;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El workspace es la fachada que ve el estudiante. Lo que se fija aqui es que
 * crear uno deje todo listo de una vez y que los numeros de la tarjeta cuadren
 * con el tablero de verdad.
 */
class WorkspaceControllerIT extends AbstractApiIT {

    @Test
    @DisplayName("Crear un workspace deja equipo, tablero y columnas listos de una vez")
    void createLeavesEverythingReady() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);

        var result = mockMvc.perform(authorized(post("/api/workspaces"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bases de Datos\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bases de Datos"))
                // Quien lo crea manda en el, aunque su rol global sea MIEMBRO.
                .andExpect(jsonPath("$.myRole").value("RESPONSABLE"))
                .andExpect(jsonPath("$.boardId").isNumber())
                .andExpect(jsonPath("$.teamId").isNumber())
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.progress").value(0))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andReturn();

        Long boardId = json(result).get("boardId").asLong();
        mockMvc.perform(authorized(get("/api/boards/{id}", boardId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.columns[0].name").value("Por hacer"))
                .andExpect(jsonPath("$.columns[1].name").value("En progreso"))
                .andExpect(jsonPath("$.columns[2].name").value("Hecho"));
    }

    @Test
    @DisplayName("El nombre es obligatorio y nada mas")
    void nameIsTheOnlyRequiredField() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(post("/api/workspaces"), user)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("La duracion del sprint se fija al crear y vale para todo el plan")
    void sprintDaysIsSetAtCreation() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(post("/api/workspaces"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bases de Datos\",\"sprintDays\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sprintDays").value(7));

        // Sin indicarla, no se inventa un valor: el cliente decide el respaldo.
        mockMvc.perform(authorized(post("/api/workspaces"), user)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sin duracion\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sprintDays").doesNotExist());
    }

    @Test
    @DisplayName("Una duracion de sprint menor que un dia se rechaza")
    void sprintDaysMustBePositive() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);

        mockMvc.perform(authorized(post("/api/workspaces"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bases de Datos\",\"sprintDays\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("La tarjeta cuenta como hechas las tareas de la ultima columna")
    void progressCountsTheLastColumn() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);
        Long workspaceId = createWorkspace(user, "Ingenieria del Software").id();

        var detail = mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), user)).andReturn();
        Long boardId = json(detail).get("boardId").asLong();
        long[] columns = columnIds(user, boardId);

        createTask(user, columns[0], "Analizar requisitos");
        createTask(user, columns[0], "Disenar el modelo");
        Long terminada = createTask(user, columns[0], "Montar el repositorio");
        mockMvc.perform(authorized(patch("/api/tasks/{id}/move", terminada), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columnId\":%d,\"position\":0}".formatted(columns[2])))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(3))
                .andExpect(jsonPath("$.doneTasks").value(1))
                .andExpect(jsonPath("$.pendingTasks").value(2))
                .andExpect(jsonPath("$.progress").value(33));
    }

    @Test
    @DisplayName("La lista solo trae los workspaces de los que uno es miembro")
    void listOnlyShowsMine() throws Exception {
        TestUser ana = registerUser(Role.MIEMBRO);
        TestUser luis = registerUser(Role.MIEMBRO);

        Long mio = createWorkspace(ana, "Mi asignatura").id();
        createWorkspace(luis, "La suya");

        mockMvc.perform(authorized(get("/api/workspaces"), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mio))
                .andExpect(jsonPath("$[0].name").value("Mi asignatura"));
    }

    @Test
    @DisplayName("Un extrano no puede ni ver ni tocar el workspace")
    void strangersAreRejected() throws Exception {
        TestUser ana = registerUser(Role.MIEMBRO);
        TestUser extrano = registerUser(Role.MIEMBRO);
        Long workspaceId = createWorkspace(ana, "Bases de Datos").id();

        mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), extrano))
                .andExpect(status().isForbidden());
        mockMvc.perform(authorized(delete("/api/workspaces/{id}", workspaceId), extrano))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un MIEMBRO del equipo lo ve pero no lo administra")
    void membersCanReadButNotManage() throws Exception {
        TestUser ana = registerUser(Role.MIEMBRO);
        TestUser luis = registerUser(Role.MIEMBRO);
        Long workspaceId = createWorkspace(ana, "Bases de Datos").id();

        var detail = mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), ana)).andReturn();
        Long teamId = json(detail).get("teamId").asLong();
        addMember(ana, teamId, luis, Role.MIEMBRO);

        mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), luis))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MIEMBRO"));

        mockMvc.perform(authorized(put("/api/workspaces/{id}", workspaceId), luis)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otro nombre\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Editar cambia nombre, color, icono y la ventana de fechas")
    void updateChangesTheCard() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);
        Long workspaceId = createWorkspace(user, "Bases de Datos").id();

        mockMvc.perform(authorized(put("/api/workspaces/{id}", workspaceId), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"BD avanzadas","description":"Practica 3","color":"tinta",
                                 "icon":"BDA","startDate":"2026-10-01","deadline":"2026-12-15","sprintDays":7}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("BD avanzadas"))
                .andExpect(jsonPath("$.color").value("tinta"))
                .andExpect(jsonPath("$.icon").value("BDA"))
                // Las dos fechas y la duracion del sprint son las que el plan usa para sus sprints.
                .andExpect(jsonPath("$.startDate").value("2026-10-01"))
                .andExpect(jsonPath("$.deadline").value("2026-12-15"))
                .andExpect(jsonPath("$.sprintDays").value(7));

        mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-10-01"));
    }

    @Test
    @DisplayName("Borrar el workspace se lleva por delante su tablero")
    void deleteRemovesTheBoard() throws Exception {
        TestUser user = registerUser(Role.MIEMBRO);
        Long workspaceId = createWorkspace(user, "Para borrar").id();
        var detail = mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), user)).andReturn();
        Long boardId = json(detail).get("boardId").asLong();

        mockMvc.perform(authorized(delete("/api/workspaces/{id}", workspaceId), user))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/workspaces/{id}", workspaceId), user))
                .andExpect(status().isForbidden());
        mockMvc.perform(authorized(get("/api/boards/{id}", boardId), user))
                .andExpect(status().isForbidden());
    }
}
