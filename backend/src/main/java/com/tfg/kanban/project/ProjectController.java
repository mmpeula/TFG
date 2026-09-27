package com.tfg.kanban.project;

import com.tfg.kanban.project.dto.ProjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * El proyecto que hay debajo de cada workspace. Crearlo, editarlo y borrarlo se
 * hace desde /api/workspaces, que lleva de una vez el equipo, el proyecto y el
 * tablero; aqui solo queda leerlo.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Tag(name = "Proyectos", description = "Practicas y trabajos de cada equipo")
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "Detalle de un proyecto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proyecto encontrado"),
            @ApiResponse(responseCode = "403", description = "No perteneces al equipo del proyecto"),
            @ApiResponse(responseCode = "404", description = "No existe")
    })
    @PreAuthorize("@access.canReadProject(#id)")
    @GetMapping("/{id}")
    public ProjectResponse findById(@PathVariable Long id) {
        return projectService.findById(id);
    }
}
