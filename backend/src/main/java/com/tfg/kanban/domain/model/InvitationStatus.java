package com.tfg.kanban.domain.model;

/**
 * En que punto esta una invitacion a un equipo.
 *
 * <p>Solo {@link #PENDIENTE} sale en la pantalla de miembros; las demas se
 * conservan para saber que paso con cada invitacion en lugar de borrar la fila,
 * que dejaria sin explicacion por que alguien entro (o no) en el equipo.</p>
 */
public enum InvitationStatus {
    /** Enviada y a la espera de que la persona invitada responda. */
    PENDIENTE,
    /** La persona acepto: ya es miembro del equipo. */
    ACEPTADA,
    /** La persona dijo que no. */
    RECHAZADA,
    /** Quien invito se arrepintio antes de que respondieran. */
    CANCELADA
}
