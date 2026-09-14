package com.tfg.kanban.ai;

/**
 * El modelo local no esta disponible: apagado, inalcanzable o ha tardado
 * demasiado. Se traduce a HTTP 503 para distinguirlo de un error del servidor.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
