package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.AiPlanResponse;
import com.tfg.kanban.ai.dto.ChatRequest;
import com.tfg.kanban.ai.dto.ChatResponse;
import com.tfg.kanban.common.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * El copiloto de un workspace.
 *
 * <p>Dos cosas distintas conviven aqui a proposito: <b>preguntar</b>, que solo
 * devuelve texto y nunca escribe, y <b>proponer tareas</b>, que devuelve una
 * propuesta que el usuario tiene que aceptar despues. El modelo no toca el
 * tablero por su cuenta en ninguno de los dos casos.</p>
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/copilot")
@RequiredArgsConstructor
@Tag(name = "IA", description = "Planificacion automatica y copiloto del workspace")
public class CopilotController {

    private final CopilotChatService copilotChatService;
    private final AiPlanService aiPlanService;

    @Operation(summary = "Preguntar al copiloto",
            description = """
                    Responde en texto sobre el estado real del workspace: tareas, columnas,
                    fechas, personas y comentarios. No modifica nada.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Respuesta generada"),
            @ApiResponse(responseCode = "403", description = "No se pertenece a este workspace",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "El modelo local no esta disponible",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize("@access.canReadProject(#workspaceId)")
    @PostMapping("/chat")
    public ChatResponse chat(@PathVariable Long workspaceId, @Valid @RequestBody ChatRequest request) {
        return copilotChatService.answer(workspaceId, request);
    }

    @Operation(summary = "Proponer tareas a partir de una descripcion",
            description = """
                    Convierte una descripcion escrita a mano en una propuesta de tareas
                    repartidas. No crea nada: se aplican con
                    POST /api/projects/{id}/ai-plan/apply.
                    """)
    @PreAuthorize("@access.canManageProject(#workspaceId)")
    @PostMapping("/tasks")
    public AiPlanResponse proposeFromText(@PathVariable Long workspaceId,
                                          @Valid @RequestBody TextRequest request,
                                          @Parameter(description = "Entre cuantas personas repartir")
                                          @RequestParam(required = false) Integer people) {
        return aiPlanService.generateFromText(workspaceId, request.text(), people, request.indications());
    }

    @Schema(description = "Descripcion libre del trabajo, mas indicaciones opcionales para el reparto", example = """
            {"text":"Tenemos que hacer una practica de bases de datos con modelo E/R y consultas SQL",
             "indications":"Ana esta liada esta semana, dale menos carga que al resto"}
            """)
    public record TextRequest(
            @NotBlank(message = "escribe de que va el trabajo") String text,
            @Schema(description = "Indicaciones a tener en cuenta ademas del texto, con prioridad sobre el si hay conflicto")
            String indications
    ) {
    }
}
