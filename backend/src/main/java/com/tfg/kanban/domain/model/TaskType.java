package com.tfg.kanban.domain.model;

/**
 * De que va la tarea.
 *
 * Es lo unico que colorea la interfaz: el estado se lee en la columna y la
 * prioridad en su etiqueta, asi que reservar el color para el tipo evita que
 * dos lenguajes visuales compitan en la misma tarjeta.
 *
 * <p>Las tareas creadas antes de existir este campo lo tienen a nulo; se leen
 * como {@link #TAREA}, que es el caso corriente.</p>
 */
public enum TaskType {

    /** Un bloque grande de trabajo que agrupa a otros. */
    EPICA,

    /** Algo que aporta valor a quien usa el producto. */
    HISTORIA,

    /** Trabajo concreto y acotado. Es el valor por defecto. */
    TAREA,

    /** Algo que no funciona como deberia. */
    BUG,

    /** Un imprevisto que hay que resolver para poder seguir. */
    INCIDENCIA
}
