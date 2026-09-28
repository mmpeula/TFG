package com.tfg.kanban.user.dto;

import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Datos publicos de un usuario", example = """
        {"id":1,"email":"ana@uni.es","fullName":"Ana Garcia","role":"RESPONSABLE","enabled":true}
        """)
public record UserResponse(
        Long id,
        String email,
        String fullName,
        @Schema(description = "Rol global del usuario") Role role,
        boolean enabled,
        Instant createdAt,
        @Schema(description = "URL de la foto de perfil; null si no tiene y se muestran las iniciales")
        String avatarUrl
) {

    public static UserResponse from(User user) {
        String avatarUrl = user.getAvatarStoredName() == null ? null : "/api/users/%d/avatar".formatted(user.getId());
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.isEnabled(), user.getCreatedAt(), avatarUrl);
    }
}
