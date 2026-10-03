package com.tfg.kanban.user.dto;

import com.tfg.kanban.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Rol global al que pasa la cuenta (DOCENTE o ADMIN; solo se sube de rol)", example = "{\"role\":\"DOCENTE\"}")
public record UpdateGlobalRoleRequest(@NotNull Role role) {
}
