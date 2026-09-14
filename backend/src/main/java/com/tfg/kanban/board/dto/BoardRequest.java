package com.tfg.kanban.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos de un tablero", example = """
        {"name":"Tablero principal","description":"Seguimiento de la practica"}
        """)
public record BoardRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description
) {
}
