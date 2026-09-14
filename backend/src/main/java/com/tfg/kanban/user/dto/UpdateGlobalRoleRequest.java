package com.tfg.kanban.user.dto;

import com.tfg.kanban.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Cambio del rol global de un usuario", example = "{\"role\":\"OBSERVADOR\"}")
public record UpdateGlobalRoleRequest(@NotNull Role role) {
}
