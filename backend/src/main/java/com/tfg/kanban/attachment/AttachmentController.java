package com.tfg.kanban.attachment;

import com.tfg.kanban.attachment.dto.AttachmentResponse;
import com.tfg.kanban.domain.model.Attachment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Adjuntos", description = "Ficheros asociados a una tarea")
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Operation(summary = "Adjuntos de una tarea")
    @PreAuthorize("@access.canReadTask(#taskId)")
    @GetMapping("/api/tasks/{taskId}/attachments")
    public List<AttachmentResponse> findByTask(@PathVariable Long taskId) {
        return attachmentService.findByTask(taskId);
    }

    @Operation(summary = "Subir un adjunto",
            description = """
                    RESPONSABLE o MIEMBRO. Peticion multipart/form-data con el campo 'file'.
                    Se validan el tipo MIME (imagenes, PDF, texto, ofimatica y zip) y el tamano
                    maximo configurado en app.storage.max-file-size (10 MB por defecto).
                    """)
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(type = "object", requiredProperties = "file")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Adjunto guardado"),
            @ApiResponse(responseCode = "409", description = "Tipo o tamano de fichero no permitido")
    })
    @PreAuthorize("@access.canWriteTask(#taskId)")
    @PostMapping(value = "/api/tasks/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(@PathVariable Long taskId,
                                                     @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.upload(taskId, file));
    }

    @Operation(summary = "Descargar un adjunto",
            description = "Devuelve el contenido del fichero. Cualquier miembro del equipo puede descargarlo.")
    @PreAuthorize("@access.canReadAttachment(#id)")
    @GetMapping("/api/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Attachment attachment = attachmentService.getAttachment(id);
        Resource resource = attachmentService.loadContent(attachment);

        String encoded = URLEncoder.encode(attachment.getFilename(), StandardCharsets.UTF_8).replace("+", "%20");
        String contentType = attachment.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : attachment.getContentType();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentLength(attachment.getSizeBytes())
                .body(resource);
    }

    @Operation(summary = "Borrar un adjunto",
            description = "Quien lo subio o el RESPONSABLE del equipo.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Adjunto borrado"))
    @PreAuthorize("@access.canDeleteAttachment(#id)")
    @DeleteMapping("/api/attachments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
