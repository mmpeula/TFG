package com.tfg.kanban.workspace.dto;

import com.tfg.kanban.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Un workspace visto desde el panel de administracion, para dar soporte.
 *
 * <p>Lleva datos de diagnostico, no contenido: cuanta gente hay, cuantas tareas
 * y cuando se movio por ultima vez, pero ni una sola tarea, comentario o
 * fichero. Administrar la plataforma no da derecho a leer el trabajo de nadie,
 * y por eso ese contenido no sale de aqui aunque lo pidiera la interfaz.</p>
 *
 * @param enabled false si soporte lo tiene suspendido: el equipo no puede abrirlo
 *                ni tocar nada suyo, pero no se ha perdido nada y reactivarlo lo
 *                deja como estaba.
 * @param blocked equipo sin ninguna persona responsable con la cuenta activa.
 *                Es el caso que de verdad necesita soporte: nadie puede
 *                administrar ese workspace, invitar ni cambiar roles, y el
 *                equipo no puede arreglarlo por su cuenta.
 */
@Schema(description = "Workspace visto desde administracion, con datos de diagnostico", example = """
        {"id":7,"name":"Practica de Bases de Datos","teamId":3,"ownerName":"Ana Garcia",
         "taskCount":12,"enabled":true,"blocked":false,"createdAt":"2026-09-01T10:00:00Z",
         "lastActivityAt":"2026-09-20T18:30:00Z",
         "members":[{"id":1,"fullName":"Ana Garcia","email":"ana@uni.es","role":"RESPONSABLE","enabled":true}]}
        """)
public record AdminWorkspaceResponse(
        Long id,
        String name,
        Long teamId,
        String ownerName,
        int taskCount,
        boolean enabled,
        boolean blocked,
        Instant createdAt,
        Instant lastActivityAt,
        List<Member> members
) {

    /** Quien esta en el equipo, con lo justo para poder desbloquearlo. */
    public record Member(Long id, String fullName, String email, Role role, boolean enabled) {
    }
}
