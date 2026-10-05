package com.tfg.kanban.common;

/**
 * Resultado de un conteo agrupado por entidad.
 * Se usa para traer de una sola consulta los comentarios de un lote de tareas
 * y evitar el problema N+1 al pintar el tablero.
 */
public record CountByEntity(Long entityId, long total) {
}
