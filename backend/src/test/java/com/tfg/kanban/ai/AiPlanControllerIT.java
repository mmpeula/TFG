package com.tfg.kanban.ai;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * En los tests el modulo de IA esta apagado (app.ai.enabled=false), asi que no se
 * habla con ningun modelo: lo que se fija aqui es el contrato del endpoint, que es
 * justo donde ya se colo un fallo (el @RequestBody de Swagger tapando al de Spring).
 */
class AiPlanControllerIT extends AbstractApiIT {

    private record Contexto(TestUser responsable, TestUser miembro, Long projectId, Long boardId) {
    }

    private Contexto contexto() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser miembro = registerUser(Role.MIEMBRO);
        Workspace workspace = createWorkspace(responsable, "Equipo IA");
        Long teamId = workspace.teamId();
        addMember(responsable, teamId, miembro, Role.MIEMBRO);
        Long projectId = workspace.id();
        Long boardId = workspace.boardId();
        return new Contexto(responsable, miembro, projectId, boardId);
    }

    @Test
    @DisplayName("El cuerpo de «aplicar» se enlaza y crea las tareas aceptadas con su responsable")
    void applyBindsBodyAndAssigns() throws Exception {
        Contexto ctx = contexto();

        String body = """
                {"boardId":%d,"tasks":[
                  {"title":"Modelar el esquema","reason":"Lo pide el guion","tag":"analisis",
                   "priority":"ALTA","estimatedHours":6,"assigneeIds":[%d],"assigneeNames":["Quien sea"]}
                ]}
                """.formatted(ctx.boardId(), ctx.miembro().id());

        mockMvc.perform(authorized(post("/api/projects/{id}/ai-plan/apply", ctx.projectId()), ctx.responsable())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Modelar el esquema"))
                .andExpect(jsonPath("$[0].aiGenerated").value(true))
                .andExpect(jsonPath("$[0].assignees[0].id").value(ctx.miembro().id()))
                .andExpect(jsonPath("$[0].columnName").value("Por hacer"));
    }

    @Test
    @DisplayName("Aplicar sin tareas se rechaza con el error de validacion")
    void applyRejectsEmptySelection() throws Exception {
        Contexto ctx = contexto();

        mockMvc.perform(authorized(post("/api/projects/{id}/ai-plan/apply", ctx.projectId()), ctx.responsable())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardId\":%d,\"tasks\":[]}".formatted(ctx.boardId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Un MIEMBRO no puede lanzar ni aplicar la planificacion")
    void onlyResponsableCanUseAi() throws Exception {
        Contexto ctx = contexto();

        mockMvc.perform(authorized(post("/api/projects/{id}/ai-plan", ctx.projectId()), ctx.miembro()))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/projects/{id}/ai-plan/document", ctx.projectId())
                        .file(new MockMultipartFile("file", "guion.txt", "text/plain",
                                "contenido".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, ctx.miembro().bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El progreso solo lo ve el responsable y, sin generacion en curso, no trae ninguna fase")
    void progressIsPrivateAndEmptyWhenIdle() throws Exception {
        Contexto ctx = contexto();

        mockMvc.perform(authorized(get("/api/projects/{id}/ai-plan/progress", ctx.projectId()), ctx.responsable()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").doesNotExist());

        mockMvc.perform(authorized(get("/api/projects/{id}/ai-plan/progress", ctx.projectId()), ctx.miembro()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Con la IA apagada se responde 503, no un 500")
    void reportsUnavailableWhenDisabled() throws Exception {
        Contexto ctx = contexto();

        mockMvc.perform(multipart("/api/projects/{id}/ai-plan/document", ctx.projectId())
                        .file(new MockMultipartFile("file", "guion.txt", "text/plain",
                                "Practica de biblioteca".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, ctx.responsable().bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Los ajustes de la generacion viajan como parte JSON del multipart")
    void acceptsOptionsPartAlongsideTheDocument() throws Exception {
        Contexto ctx = contexto();
        String options = """
                {"startDate":"2027-01-04","sprintDays":14,
                 "team":[{"userId":%d,"preferences":"Backend; Java"}]}
                """.formatted(ctx.miembro().id());

        // Con la IA apagada se llega hasta el modelo y responde 503: prueba que la
        // parte se ha leido bien, porque un JSON mal enlazado daria 400 antes.
        mockMvc.perform(multipart("/api/projects/{id}/ai-plan/document", ctx.projectId())
                        .file(new MockMultipartFile("file", "guion.txt", "text/plain",
                                "Practica de biblioteca".getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("options", "", MediaType.APPLICATION_JSON_VALUE,
                                options.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, ctx.responsable().bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Unos ajustes invalidos se rechazan con el error de validacion")
    void rejectsInvalidOptions() throws Exception {
        Contexto ctx = contexto();

        mockMvc.perform(authorized(post("/api/projects/{id}/ai-plan", ctx.projectId()), ctx.responsable())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sprintDays\":90}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Un documento con formato no admitido se rechaza antes de llamar al modelo")
    void rejectsUnsupportedDocument() throws Exception {
        Contexto ctx = contexto();

        mockMvc.perform(multipart("/api/projects/{id}/ai-plan/document", ctx.projectId())
                        .file(new MockMultipartFile("file", "guion.pages", "application/octet-stream", "x".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, ctx.responsable().bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }
}
