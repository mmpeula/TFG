package com.tfg.kanban.comment.dto;

import com.tfg.kanban.domain.model.Comment;
import com.tfg.kanban.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Comentario de una tarea", example = """
        {"id":4,"content":"He terminado el apartado 2","taskId":7,
         "author":{"id":1,"fullName":"Ana Garcia","email":"ana@uni.es"},
         "edited":false,"createdAt":"2026-08-21T10:15:30Z","updatedAt":"2026-08-21T10:15:30Z"}
        """)
public record CommentResponse(
        Long id,
        String content,
        Long taskId,
        UserSummary author,
        boolean edited,
        Instant createdAt,
        Instant updatedAt
) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(comment.getId(), comment.getContent(), comment.getTask().getId(),
                UserSummary.from(comment.getAuthor()), comment.isEdited(),
                comment.getCreatedAt(), comment.getUpdatedAt());
    }
}
