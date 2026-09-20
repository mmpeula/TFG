package com.tfg.kanban.domain.model;

/**
 * Los cuatro roles del sistema (RBAC).
 *
 * <p>Se reparten en dos planos distintos. {@link #ADMIN} y {@link #DOCENTE} son
 * roles <b>de cuenta</b>: dicen que es esa persona en la plataforma y con que
 * panel se encuentra al entrar. {@link #RESPONSABLE} y {@link #MIEMBRO} son
 * roles <b>de equipo</b>: una misma cuenta de estudiante puede ser responsable
 * de un equipo y miembro raso de otro, asi que para cada recurso manda el que
 * tiene en el equipo dueno del recurso (vease {@code TeamMember} y
 * {@code AccessService}).</p>
 *
 * <ul>
 *   <li>{@link #ADMIN}: administra las cuentas de la plataforma
 *       ({@code UserController}). No pertenece a ningun equipo.</li>
 *   <li>{@link #DOCENTE}: sigue en modo lectura los equipos a los que se le anade.</li>
 *   <li>{@link #RESPONSABLE}: coordina el equipo, crea proyectos/tableros y asigna tareas.</li>
 *   <li>{@link #MIEMBRO}: participa en el trabajo, mueve y edita sus tareas. Es el
 *       rol con el que nace toda cuenta nueva; ADMIN y DOCENTE los concede un
 *       administrador, nunca se obtienen al registrarse.</li>
 * </ul>
 *
 * Spring Security espera autoridades con prefijo {@code ROLE_}; la conversion se hace
 * en {@code UserDetails} (ver {@code AppUserDetailsService}).
 */
public enum Role {
    RESPONSABLE,
    MIEMBRO,
    DOCENTE,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
