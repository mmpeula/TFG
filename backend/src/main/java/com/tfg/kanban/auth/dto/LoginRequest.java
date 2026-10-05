package com.tfg.kanban.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciales de acceso", example = """
        {"email":"ana@uni.es","password":"claveSegura1"}
        """)
public record LoginRequest(

        @NotBlank(message = "el email es obligatorio")
        String email,

        @NotBlank(message = "la contraseña es obligatoria")
        String password
) {
}
