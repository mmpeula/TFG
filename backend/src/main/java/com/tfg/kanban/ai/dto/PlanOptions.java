package com.tfg.kanban.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que se decide en el formulario de Generar plan para esta ejecucion.
 *
 * <p>Todo es opcional: lo que no venga se toma del espacio de trabajo (fecha de
 * inicio y duracion del sprint) o del equipo (quien entra en el reparto, con su
 * ficha como preferencias). Nada de esto se guarda: vale para esta generacion y
 * se acaba ahi.</p>
 */
@Schema(description = "Ajustes de esta generacion de plan", example = """
        {"startDate":"2026-10-01","sprintDays":14,
         "team":[{"userId":2,"preferences":"Backend; Java y Spring"},
                 {"userId":3,"preferences":"Frontend; React; solo la mitad de horas esta sprint"}]}
        """)
public record PlanOptions(

        @Schema(description = "Cuando empieza el primer sprint. Por defecto, la del espacio de trabajo o hoy")
        LocalDate startDate,

        @Schema(description = "Fecha de entrega. Por defecto, la del espacio de trabajo; sin ninguna, el numero "
                + "de sprints lo decide el modelo")
        LocalDate deadline,

        @Schema(description = "Dias de cada sprint. Por defecto, los del espacio de trabajo o 14")
        @Min(value = 7, message = "un sprint dura al menos una semana")
        @Max(value = 28, message = "un sprint dura como mucho cuatro semanas")
        Integer sprintDays,

        @Schema(description = "Quien entra en el reparto. Vacio o ausente: todo el equipo menos el docente")
        @Valid
        List<TeamSlot> team
) {

    /** Sin nada ajustado: todo sale del espacio de trabajo y del equipo. */
    public static PlanOptions defaults() {
        return new PlanOptions(null, null, null, null);
    }

    /**
     * Una persona del equipo tal y como entra en este reparto. Solo cuenta si
     * {@code userId} es de un miembro real: las tareas tienen que poder asignarse
     * en el tablero, y ahi solo cabe gente del equipo.
     */
    @Schema(description = "Una persona del reparto con sus datos para esta generacion")
    public record TeamSlot(
            Long userId,

            @Schema(description = "Preferencias y restricciones, incluida la disponibilidad si es distinta de lo "
                    + "habitual (menos horas, examenes, vacaciones...). Por defecto, su ficha de Miembros")
            @Size(max = 600)
            String preferences
    ) {
    }
}
