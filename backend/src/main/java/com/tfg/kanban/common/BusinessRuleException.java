package com.tfg.kanban.common;

/** Regla de negocio incumplida (p. ej. dejar un equipo sin responsable). Se traduce a HTTP 409. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
