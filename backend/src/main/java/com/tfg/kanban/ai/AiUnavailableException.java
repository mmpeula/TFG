package com.tfg.kanban.ai;

/**
 * Groq no esta disponible: la API key no vale, no hay conexion o ha tardado
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
