package com.tfg.kanban.user.dto;

import com.tfg.kanban.domain.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collection;
import java.util.List;

@Schema(description = "Usuario resumido", example = """
        {"id":1,"fullName":"Ana Garcia","email":"ana@uni.es"}
        """)
public record UserSummary(Long id, String fullName, String email, String avatarUrl) {

    public static UserSummary from(User user) {
        if (user == null) {
            return null;
        }
        String avatarUrl = user.getAvatarStoredName() == null ? null : "/api/users/%d/avatar".formatted(user.getId());
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail(), avatarUrl);
    }

    /** El orden ya llega correcto: la coleccion de origen esta ordenada por nombre. */
    public static List<UserSummary> fromAll(Collection<User> users) {
        return users == null ? List.of() : users.stream().map(UserSummary::from).toList();
    }
}
