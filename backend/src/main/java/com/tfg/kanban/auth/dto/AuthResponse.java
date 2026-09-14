package com.tfg.kanban.auth.dto;

import com.tfg.kanban.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token de acceso y datos del usuario autenticado", example = """
        {"token":"eyJhbGciOiJIUzI1NiJ9...","tokenType":"Bearer","expiresInMinutes":120,
         "user":{"id":1,"email":"ana@uni.es","fullName":"Ana Garcia","role":"RESPONSABLE","enabled":true}}
        """)
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMinutes,
        UserResponse user
) {

    public static AuthResponse of(String token, long expiresInMinutes, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresInMinutes, user);
    }
}
