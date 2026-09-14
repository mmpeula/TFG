package com.tfg.kanban.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos editables del perfil", example = """
        {"fullName":"Ana Garcia Lopez","password":"nuevaClave123"}
        """)
public record UpdateUserRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 150)
        String fullName,

        @Schema(description = "Nueva contrasena; si se omite, no se cambia")
        @Size(min = 8, max = 72, message = "la contrasena debe tener entre 8 y 72 caracteres")
        String password
) {
}
