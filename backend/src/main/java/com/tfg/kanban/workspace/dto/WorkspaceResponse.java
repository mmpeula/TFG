package com.tfg.kanban.workspace.dto;

import com.tfg.kanban.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Un workspace tal y como lo ve el estudiante en la pantalla inicial.
 *
 * <p>Detras es un proyecto con su equipo y su tablero, pero eso no se muestra:
 * la tarjeta solo lleva lo que sirve para decidir en cual entrar.</p>
 */
@Schema(description = "Workspace: una asignatura, practica o trabajo", example = """
        {"id":5,"name":"Bases de Datos","description":"Practica 2 - modelo E/R",
         "color":"arena","icon":"BD","teamId":1,"boardId":9,"myRole":"RESPONSABLE",
         "totalTasks":8,"doneTasks":3,"pendingTasks":5,"progress":37,"memberCount":4,
         "startDate":"2026-10-01","deadline":"2026-12-15","sprintDays":14,
         "lastActivityAt":"2026-09-02T18:04:00Z"}
        """)
public record WorkspaceResponse(
        Long id,
        String name,
        String description,
        @Schema(description = "Nombre del color elegido; el cliente decide como pintarlo") String color,
        @Schema(description = "Iniciales o emoji para reconocerlo de un vistazo") String icon,
        Long teamId,
        @Schema(description = "Tablero principal. Nulo solo si se borro a mano") Long boardId,
        Role myRole,
        int totalTasks,
        int doneTasks,
        int pendingTasks,
        @Schema(description = "Porcentaje de tareas terminadas, de 0 a 100") int progress,
        int memberCount,
        @Schema(description = "Inicio del trabajo; con la entrega define la ventana de sprints") LocalDate startDate,
        LocalDate deadline,
        @Schema(description = "Duracion del sprint en dias, la misma para todo el plan") Integer sprintDays,
        @Schema(description = "Ultimo movimiento registrado en el workspace") Instant lastActivityAt,
        @Schema(description = "False si soporte lo tiene suspendido: sigue en la lista, pero no se "
                + "puede abrir ni tocar nada suyo hasta que lo reactiven")
        boolean enabled
) {
}
