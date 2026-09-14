package com.tfg.kanban.common;

/** El recurso solicitado no existe. Se traduce a HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super("%s con id %s no encontrado".formatted(resource, id));
    }
}
