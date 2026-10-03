package com.tfg.kanban.ai;

/**
 * En que punto va la generacion de un plan a partir de un documento. Es lo que
 * la interfaz ensena mientras espera; «planificacion completada» no esta aqui
 * porque lo dice la propia respuesta, no hace falta preguntarlo.
 */
public enum ProcessingStage {
    RECEIVED,
    ANALYZING,
    EXTRACTING,
    VISUAL,
    PLANNING
}
