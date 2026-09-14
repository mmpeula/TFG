package com.tfg.kanban.project;

import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.domain.model.ProjectStatus;
import com.tfg.kanban.project.dto.CreateProjectRequest;
import com.tfg.kanban.project.dto.ProjectResponse;
import com.tfg.kanban.project.dto.UpdateProjectRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Tag(name = "Proyectos", description = "Practicas y trabajos de cada equipo")
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "Listar proyectos visibles",
            description = "Proyectos de los equipos a los que pertenece el usuario. Admite filtro por equipo y estado.")
    @GetMapping
    public PageResponse<ProjectResponse> findVisible(
            @Parameter(description = "Filtrar por equipo") @RequestParam(required = false) Long teamId,
            @Parameter(description = "Filtrar por estado") @RequestParam(required = false) ProjectStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return projectService.findVisible(teamId, status, pageable);
    }

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

    @Operation(summary = "Crear un proyecto", description = "Solo el RESPONSABLE del equipo indicado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Proyecto creado"),
            @ApiResponse(responseCode = "403", description = "No eres RESPONSABLE del equipo"),
            @ApiResponse(responseCode = "409", description = "Ya existe un proyecto con ese nombre en el equipo")
    })
    @PreAuthorize("@access.canManageTeam(#request.teamId())")
    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
    }

    @Operation(summary = "Editar un proyecto", description = "Solo el RESPONSABLE del equipo.")
    @PreAuthorize("@access.canManageProject(#id)")
    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(id, request);
    }

    @Operation(summary = "Borrar un proyecto",
            description = "Solo el RESPONSABLE. Arrastra tableros, columnas, tareas, comentarios y adjuntos.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Proyecto borrado"))
    @PreAuthorize("@access.canManageProject(#id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
