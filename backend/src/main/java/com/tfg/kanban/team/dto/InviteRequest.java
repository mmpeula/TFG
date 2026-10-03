package com.tfg.kanban.team.dto;

import com.tfg.kanban.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Invitacion a alguien para que entre en el equipo", example = """
        {"email":"luis@uni.es","role":"MIEMBRO"}
        """)
public record InviteRequest(

        @Schema(description = "Correo de una cuenta ya registrada: se invita a personas, no a direcciones sueltas")
        @NotBlank(message = "el email es obligatorio")
        @Email(message = "el email no tiene un formato valido")
        String email,

        @Schema(description = "Como entra: MIEMBRO (trabaja en el equipo) o DOCENTE (lo sigue). Sin indicarlo, el que "
                + "corresponde a su cuenta. Una cuenta docente puede entrar como MIEMBRO para trabajar en el equipo; "
                + "ser RESPONSABLE es un paso posterior, ya dentro.", example = "MIEMBRO", nullable = true)
        Role role
) {
}
