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
 *       ({@code UserController}). No pertenece a ningun equipo. Es el unico rol
 *       que no se puede pedir al registrarse: lo concede otro administrador.</li>
 *   <li>{@link #DOCENTE}: una cuenta con el mismo panel que un alumno. Puede crear
 *       sus propios espacios (y es su RESPONSABLE) y seguir en modo lectura los que
 *       otras personas le anaden como docente: lo que puede hacer en cada espacio lo
 *       decide el rol que tiene <b>en ese espacio</b>, no el de su cuenta. Se elige al
 *       crear la cuenta, o se llega a el desde alumno.</li>
 *   <li>{@link #RESPONSABLE}: coordina el equipo, crea proyectos/tableros y asigna tareas.</li>
 *   <li>{@link #MIEMBRO}: participa en el trabajo, mueve y edita sus tareas. Es lo
 *       que es una cuenta de estudiante y el valor por defecto al registrarse.</li>
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
