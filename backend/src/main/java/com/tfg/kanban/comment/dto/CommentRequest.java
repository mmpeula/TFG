package com.tfg.kanban.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Texto de un comentario", example = """
        {"content":"He terminado el apartado 2, reviso el 3 manana."}
        """)
public record CommentRequest(

        @NotBlank(message = "el comentario no puede estar vacio")
        @Size(max = 2000, message = "el comentario no puede superar los 2000 caracteres")
        String content
) {
}
