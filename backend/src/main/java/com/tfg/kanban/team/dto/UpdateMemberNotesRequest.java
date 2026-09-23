package com.tfg.kanban.team.dto;

import com.tfg.kanban.domain.model.TeamMember;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Lo que hay que saber de una persona del equipo al generar el plan.
 *
 * <p>Va en su propio endpoint y no dentro del cambio de rol porque son dos cosas
 * distintas: el rol decide lo que esa persona <b>puede hacer</b> en el workspace
 * y lo comprueba el servidor en cada peticion; esto solo orienta al modelo al
 * repartir, y equivocarse escribiendolo no abre ni cierra ningun permiso.</p>
 */
@Schema(description = "Indicaciones permanentes de un miembro para generar el plan", example = """
        {"planNotes":"Se maneja bien con Java y Spring Boot. Prefiere trabajo de backend \
        y de logica. Evitar darle diseno de interfaz si hay alternativa."}
        """)
public record UpdateMemberNotesRequest(

        @Schema(description = "Texto libre. Vacio o ausente borra las indicaciones.")
        @Size(max = TeamMember.MAX_PLAN_NOTES,
                message = "las indicaciones no pueden pasar de 600 caracteres")
        String planNotes
) {
}
