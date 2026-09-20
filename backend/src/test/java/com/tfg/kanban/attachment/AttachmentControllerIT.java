package com.tfg.kanban.attachment;

import com.tfg.kanban.AbstractApiIT;
import com.tfg.kanban.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttachmentControllerIT extends AbstractApiIT {

    private MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Subir, listar, descargar y borrar un adjunto")
    void attachmentLifecycle() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo adjuntos");
        Long projectId = createProject(responsable, teamId, "Practica adjuntos");
        Long boardId = createBoard(responsable, projectId, "Tablero adjuntos");
        Long taskId = createTask(responsable, columnIds(responsable, boardId)[0], "Tarea con adjunto");

        Long attachmentId = json(mockMvc.perform(multipart("/api/tasks/{id}/attachments", taskId)
                                .file(textFile("notas.txt", "contenido de prueba"))
                                .header(HttpHeaders.AUTHORIZATION, responsable.bearer()))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.filename").value("notas.txt"))
                        .andExpect(jsonPath("$.contentType").value("text/plain"))
                        .andReturn())
                .path("id").asLong();

        mockMvc.perform(authorized(get("/api/tasks/{id}/attachments", taskId), responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(authorized(get("/api/attachments/{id}/download", attachmentId), responsable))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''notas.txt"))
                .andExpect(content().string("contenido de prueba"));

        mockMvc.perform(authorized(delete("/api/attachments/{id}", attachmentId), responsable))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Se rechaza un tipo de fichero no permitido")
    void rejectsForbiddenContentType() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        Long teamId = createTeam(responsable, "Equipo tipos");
        Long projectId = createProject(responsable, teamId, "Practica tipos");
        Long boardId = createBoard(responsable, projectId, "Tablero tipos");
        Long taskId = createTask(responsable, columnIds(responsable, boardId)[0], "Tarea tipos");

        MockMultipartFile executable = new MockMultipartFile("file", "virus.exe",
                "application/x-msdownload", "MZ".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/tasks/{id}/attachments", taskId)
                        .file(executable)
                        .header(HttpHeaders.AUTHORIZATION, responsable.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Un DOCENTE puede descargar pero no subir adjuntos")
    void observerCannotUpload() throws Exception {
        TestUser responsable = registerUser(Role.MIEMBRO);
        TestUser observador = registerUser(Role.DOCENTE);
        Long teamId = createTeam(responsable, "Equipo adjuntos lectura");
        addMember(responsable, teamId, observador, Role.DOCENTE);
        Long projectId = createProject(responsable, teamId, "Practica adjuntos lectura");
        Long boardId = createBoard(responsable, projectId, "Tablero adjuntos lectura");
        Long taskId = createTask(responsable, columnIds(responsable, boardId)[0], "Tarea adjuntos lectura");

        mockMvc.perform(multipart("/api/tasks/{id}/attachments", taskId)
                        .file(textFile("guion.txt", "guion de la practica"))
                        .header(HttpHeaders.AUTHORIZATION, responsable.bearer()))
                .andExpect(status().isCreated());

        mockMvc.perform(authorized(get("/api/tasks/{id}/attachments", taskId), observador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(multipart("/api/tasks/{id}/attachments", taskId)
                        .file(textFile("mio.txt", "no deberia"))
                        .header(HttpHeaders.AUTHORIZATION, observador.bearer()))
                .andExpect(status().isForbidden());
    }
}
