package com.tfg.kanban.auth.dto;

import com.tfg.kanban.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Alta de un usuario nuevo", example = """
        {"email":"ana@uni.es","password":"claveSegura1","fullName":"Ana Garcia","role":"MIEMBRO"}
        """)
public record RegisterRequest(

        @NotBlank(message = "el email es obligatorio")
        @Email(message = "el email no tiene un formato válido")
        @Size(max = 180)
        String email,

        @NotBlank(message = "la contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "la contraseña debe tener entre 8 y 72 caracteres")
        String password,

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 150)
        String fullName,

        @Schema(description = "Alumno o Docente. Si se omite, Alumno. ADMIN nunca se puede pedir aqui: "
                + "eso lo concede otro administrador.")
        Role role
) {
}
