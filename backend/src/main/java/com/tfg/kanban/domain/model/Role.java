package com.tfg.kanban.domain.model;

/**
 * Roles del sistema (RBAC).
 *
 * <ul>
 *   <li>{@link #RESPONSABLE}: coordina el equipo, crea proyectos/tableros y asigna tareas.</li>
 *   <li>{@link #MIEMBRO}: participa en el trabajo, mueve y edita sus tareas.</li>
 *   <li>{@link #OBSERVADOR}: acceso de solo lectura (p. ej. el profesor de la asignatura).</li>
 * </ul>
 *
 * Spring Security espera autoridades con prefijo {@code ROLE_}; la conversion se hace
 * en {@code UserDetails} (ver {@code AppUserDetailsService}).
 */
public enum Role {
    RESPONSABLE,
    MIEMBRO,
    OBSERVADOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
