package com.tfg.kanban.team.dto;

import com.tfg.kanban.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Alta de un miembro en el equipo", example = """
        {"email":"luis@uni.es","role":"MIEMBRO"}
        """)
public record AddMemberRequest(

        @NotBlank(message = "el email es obligatorio")
        @Email(message = "el email no tiene un formato valido")
        String email,

        @Schema(description = "Rol dentro del equipo. Si se omite, MIEMBRO")
        Role role
) {
}
