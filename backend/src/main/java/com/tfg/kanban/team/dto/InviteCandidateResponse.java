package com.tfg.kanban.team.dto;

import com.tfg.kanban.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Con que roles se puede invitar a una cuenta, para ofrecer la eleccion solo cuando la hay", example = """
        {"accountRole":"DOCENTE","defaultRole":"DOCENTE","allowedRoles":["DOCENTE","MIEMBRO"]}
        """)
public record InviteCandidateResponse(
        @Schema(description = "El tipo de cuenta de esa persona en la plataforma")
        Role accountRole,
        @Schema(description = "Como entra si quien invita no elige otro rol")
        Role defaultRole,
        @Schema(description = "Los roles de equipo con los que se la puede invitar; vacio si no cabe en ningun equipo")
        List<Role> allowedRoles
) {
}
