package com.tfg.kanban.attachment.dto;

import com.tfg.kanban.domain.model.Attachment;
import com.tfg.kanban.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Fichero adjunto a una tarea", example = """
        {"id":2,"filename":"memoria-v1.pdf","contentType":"application/pdf","sizeBytes":248310,
         "taskId":7,"uploadedBy":{"id":1,"fullName":"Ana Garcia","email":"ana@uni.es"},
         "downloadUrl":"/api/attachments/2/download","uploadedAt":"2026-08-21T10:15:30Z"}
        """)
public record AttachmentResponse(
        Long id,
        String filename,
        String contentType,
        long sizeBytes,
        Long taskId,
        UserSummary uploadedBy,
        String downloadUrl,
        Instant uploadedAt
) {

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getFilename(),
                attachment.getContentType(), attachment.getSizeBytes(), attachment.getTask().getId(),
                UserSummary.from(attachment.getUploadedBy()),
                "/api/attachments/%d/download".formatted(attachment.getId()),
                attachment.getCreatedAt());
    }
}
