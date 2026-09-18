package com.tfg.kanban.domain.model;

/**
 * Roles del sistema (RBAC).
 *
 * <p>{@link #RESPONSABLE}, {@link #MIEMBRO} y {@link #DOCENTE} son roles <b>de equipo</b>: el que cuenta es el que la
 * persona tiene en el equipo dueno del recurso (vease {@code TeamMember} y
 * {@code AccessService}), nunca su rol global.</p>
 *
 * <ul>
 *   <li>{@link #RESPONSABLE}: coordina el equipo, crea proyectos/tableros y asigna tareas.</li>
 *   <li>{@link #MIEMBRO}: participa en el trabajo, mueve y edita sus tareas.</li>
 *   <li>{@link #DOCENTE}: acceso de solo lectura para seguir un equipo.</li>
 * </ul>
 *
 * <p>{@link #ADMIN} es distinto de los tres anteriores: es un rol <b>global</b>,
 * independiente de los equipos, reservado a quien administra las cuentas de la
 * plataforma ({@code UserController}). No se obtiene al registrarse ni se puede
 * asignar como rol de equipo (vease {@code TeamService}); lo concede otro
 * administrador.</p>
 *
 * Spring Security espera autoridades con prefijo {@code ROLE_}; la conversion se hace
 * en {@code UserDetails} (ver {@code AppUserDetailsService}).
 */
public enum Role {
    RESPONSABLE,
    MIEMBRO,
    DOCENTE,
    /** @deprecated Las cuentas y asignaciones nuevas usan {@link #DOCENTE}. */
    @Deprecated
    OBSERVADOR,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
