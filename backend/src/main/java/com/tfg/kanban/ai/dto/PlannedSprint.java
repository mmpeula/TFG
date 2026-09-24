package com.tfg.kanban.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Un sprint del plan. Las fechas las calcula el servidor con la misma regla que la
 * pestana Plan, para que las tareas caigan en los sprints que luego se pintan; el
 * objetivo es lo unico que pone el modelo.
 */
@Schema(description = "Sprint del plan propuesto", example = """
        {"number":1,"start":"2026-10-01","end":"2026-10-14","goal":"Modelo de datos y autenticacion"}
        """)
public record PlannedSprint(int number, LocalDate start, LocalDate end, String goal) {
}
