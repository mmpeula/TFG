package com.tfg.kanban.activity;

import com.tfg.kanban.activity.dto.ActivityLogResponse;
import com.tfg.kanban.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/activity")
@RequiredArgsConstructor
@Tag(name = "Actividad", description = "Historial de acciones de un proyecto")
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "Historial de actividad del proyecto",
            description = "Ordenado del mas reciente al mas antiguo. Accesible a cualquier miembro del equipo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de actividad"),
            @ApiResponse(responseCode = "403", description = "No perteneces al equipo del proyecto")
    })
    @PreAuthorize("@access.canReadProject(#projectId)")
    @GetMapping
    public PageResponse<ActivityLogResponse> findByProject(
            @PathVariable Long projectId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return activityService.findByProject(projectId, pageable);
    }
}
