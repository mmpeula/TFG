package com.tfg.kanban.auth;

import org.springframework.security.core.AuthenticationException;

/** Email o contrasena incorrectos. El manejador global lo traduce a HTTP 401. */
public class InvalidCredentialsException extends AuthenticationException {

    public InvalidCredentialsException() {
        super("Email o contraseña incorrectos");
    }
}
