package com.tfg.kanban.team.dto;

import com.tfg.kanban.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Cambio de rol de un miembro dentro del equipo", example = "{\"role\":\"RESPONSABLE\"}")
public record UpdateMemberRoleRequest(@NotNull(message = "el rol es obligatorio") Role role) {
}
