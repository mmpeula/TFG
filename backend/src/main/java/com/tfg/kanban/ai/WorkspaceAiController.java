package com.tfg.kanban.ai;

import com.tfg.kanban.ai.dto.AiPlanResponse;
import com.tfg.kanban.ai.dto.ChatRequest;
import com.tfg.kanban.ai.dto.ChatResponse;
import com.tfg.kanban.ai.dto.PlanOptions;
import com.tfg.kanban.common.ApiError;
import io.swagger.v3.oas.annotations.Operation;
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
 * Lo que la IA hace sobre un workspace.
 *
 * <p>Dos cosas distintas conviven aqui a proposito: <b>preguntar</b>, que solo
 * devuelve texto y nunca escribe, y <b>proponer tareas</b>, que devuelve una
 * propuesta que el usuario tiene que aceptar despues. El modelo no toca el
 * tablero por su cuenta en ninguno de los dos casos.</p>
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/ai")
@RequiredArgsConstructor
@Tag(name = "IA", description = "Generar plan y asistente del espacio de trabajo")
public class WorkspaceAiController {

    private final AssistantChatService assistantChatService;
    private final AiPlanService aiPlanService;

    @Operation(summary = "Preguntar al asistente",
            description = """
                    Responde en texto sobre el estado real del espacio de trabajo: tareas, columnas,
                    fechas, personas y comentarios. No modifica nada. RESPONSABLE o MIEMBRO: el
                    asistente ayuda a hacer el trabajo, y el DOCENTE solo lo sigue.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Respuesta generada"),
            @ApiResponse(responseCode = "403", description = "No se trabaja en este espacio de trabajo",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "DeepSeek no esta disponible",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize("@access.canWriteProject(#workspaceId)")
    @PostMapping("/chat")
    public ChatResponse chat(@PathVariable Long workspaceId, @Valid @RequestBody ChatRequest request) {
        return assistantChatService.answer(workspaceId, request);
    }

    @Operation(summary = "Proponer tareas a partir de una descripcion",
            description = """
                    Convierte una descripcion escrita a mano en una propuesta de tareas
                    repartidas. No crea nada: se aplican con
                    POST /api/projects/{id}/ai-plan/apply.
                    """)
    @PreAuthorize("@access.canManageProject(#workspaceId)")
    @PostMapping("/plan")
    public AiPlanResponse proposeFromText(@PathVariable Long workspaceId,
                                          @Valid @RequestBody TextRequest request) {
        return aiPlanService.generateFromText(workspaceId, request.text(), request.options());
    }

    @Schema(description = "Descripcion libre del trabajo, mas los ajustes de esta generacion", example = """
            {"text":"Tenemos que hacer una practica de bases de datos con modelo E/R y consultas SQL",
             "options":{"startDate":"2026-10-01","sprintDays":14}}
            """)
    public record TextRequest(
            @NotBlank(message = "escribe de qué va el trabajo") String text,
            @Valid PlanOptions options
    ) {
    }
}
