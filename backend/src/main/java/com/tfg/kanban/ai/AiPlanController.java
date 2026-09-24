package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.AiPlanResponse;
import com.tfg.kanban.ai.dto.ApplyPlanRequest;
import com.tfg.kanban.ai.dto.PlanOptions;
import com.tfg.kanban.common.ApiError;
import com.tfg.kanban.task.dto.TaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Planificacion automatica con un modelo que corre en local (Ollama).
 *
 * Son dos pasos a proposito: <b>generar</b> no toca el tablero y <b>aplicar</b>
 * crea solo las tareas que el usuario ha aceptado. El modelo nunca escribe
 * directamente en el proyecto.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/ai-plan")
@RequiredArgsConstructor
@Tag(name = "IA", description = "Planificacion automatica a partir del guion de la practica")
public class AiPlanController {

    private final AiPlanService aiPlanService;

    @Operation(summary = "Generar una propuesta de planificacion",
            description = """
                    Envia al modelo local el guion de la practica, el calendario, el equipo con
                    sus horas y preferencias y las tareas que ya existen, y devuelve un plan por
                    sprints. El plan se valida en el servidor y, si incumple alguna regla, se le
                    devuelve al modelo para que lo corrija. No crea nada: para llevar las tareas
                    al tablero hay que aceptarlas con POST /api/projects/{projectId}/ai-plan/apply.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Propuesta generada"),
            @ApiResponse(responseCode = "403", description = "Solo el RESPONSABLE del proyecto puede lanzarla",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "El proyecto no tiene guion de la practica",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "El modelo local no esta disponible",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize("@access.canManageProject(#projectId)")
    @PostMapping
    public AiPlanResponse generate(
            @PathVariable Long projectId,
            @Valid @RequestBody(required = false) PlanOptions options) {
        return aiPlanService.generate(projectId, options);
    }

    @Operation(summary = "Generar la planificacion a partir de un documento",
            description = """
                    Sube el documento de la actividad (PDF, Word .docx o texto plano), extrae su
                    contenido, lo guarda como guion del proyecto y devuelve las tareas propuestas
                    ya repartidas entre las personas del equipo. Tampoco crea nada en el tablero.
                    """)
    // Anotacion de Swagger, escrita con el paquete completo a proposito: importarla
    // taparia al @RequestBody de Spring que usa el metodo apply() de mas abajo.
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(type = "object", requiredProperties = "file"),
                    schemaProperties = {
                            @SchemaProperty(name = "file",
                                    schema = @Schema(type = "string", format = "binary",
                                            description = "Documento de la actividad: PDF, Word (.docx) o texto")),
                            @SchemaProperty(name = "options",
                                    schema = @Schema(implementation = PlanOptions.class,
                                            description = "Ajustes de esta generacion, como JSON (opcional)"))}))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Propuesta generada a partir del documento"),
            @ApiResponse(responseCode = "409", description = "Formato no admitido o documento sin texto legible",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "El modelo local no esta disponible",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize("@access.canManageProject(#projectId)")
    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiPlanResponse generateFromDocument(
            @PathVariable Long projectId,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart(value = "options", required = false) PlanOptions options) {
        return aiPlanService.generateFromDocument(projectId, file, options);
    }

    @Operation(summary = "Llevar al tablero las tareas aceptadas",
            description = """
                    Crea en la primera columna del tablero las tareas que el usuario ha marcado,
                    cada una con el responsable del reparto y con la marca de generadas por IA.
                    Solo se asigna a personas que pertenecen al equipo del proyecto.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tareas creadas"),
            @ApiResponse(responseCode = "403", description = "Solo el RESPONSABLE del proyecto"),
            @ApiResponse(responseCode = "409", description = "El tablero no pertenece al proyecto o no tiene columnas")
    })
    @PreAuthorize("@access.canManageProject(#projectId)")
    @PostMapping("/apply")
    public ResponseEntity<List<TaskResponse>> apply(@PathVariable Long projectId,
                                                    @Valid @RequestBody ApplyPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aiPlanService.apply(projectId, request));
    }
}
