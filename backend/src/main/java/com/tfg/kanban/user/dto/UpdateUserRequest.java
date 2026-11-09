package com.tfg.kanban.user.dto;

import com.tfg.kanban.common.validation.MaxUtf8Bytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos editables del perfil", example = """
        {"fullName":"Ana Garcia Lopez","currentPassword":"claveDeAhora","password":"nuevaClave123"}
        """)
public record UpdateUserRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 150)
        String fullName,

        /**
         * Obligatoria para cambiarse uno mismo la contrasena. No lleva
         * {@code @Size}: aqui no se valida el formato de lo que se escribe sino si
         * acierta, y decir "minimo 8 caracteres" al equivocarse ya seria contar
         * algo de la contrasena real.
         */
        @Schema(description = "Contrasena actual. Solo hace falta si se cambia la propia contrasena; "
                + "un administrador que restablece la de otra persona no la tiene ni se le pide.")
        String currentPassword,

        @Schema(description = "Nueva contrasena; si se omite, no se cambia")
        @Size(min = 8, max = 72, message = "la contraseña debe tener entre 8 y 72 caracteres")
        @MaxUtf8Bytes(value = 72, message = "la contraseña es demasiado larga: las tildes y la ñ cuentan como dos caracteres")
        String password
) {
}
