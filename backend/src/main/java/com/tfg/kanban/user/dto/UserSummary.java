package com.tfg.kanban.user.dto;

import com.tfg.kanban.domain.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Usuario resumido", example = """
        {"id":1,"fullName":"Ana Garcia","email":"ana@uni.es"}
        """)
public record UserSummary(Long id, String fullName, String email) {

    public static UserSummary from(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getFullName(), user.getEmail());
    }
}
