package com.tfg.kanban.user;

import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.user.dto.UpdateGlobalRoleRequest;
import com.tfg.kanban.user.dto.UpdateUserRequest;
import com.tfg.kanban.user.dto.UserResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Consulta y mantenimiento de cuentas")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Buscar usuarios",
            description = "Listado paginado con busqueda por nombre o email. Se usa al invitar gente a un equipo.")
    @GetMapping
    public PageResponse<UserResponse> search(
            @Parameter(description = "Texto a buscar en nombre o email") @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.search(search, pageable);
    }

    @Operation(summary = "Obtener un usuario por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
            @ApiResponse(responseCode = "404", description = "No existe")
    })
    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @Operation(summary = "Actualizar el perfil",
            description = "Solo el propio usuario o un RESPONSABLE global pueden modificar una cuenta.")
    @PreAuthorize("@access.isSelf(#id) or hasRole('RESPONSABLE')")
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @Operation(summary = "Cambiar el rol global de un usuario",
            description = "Reservado a RESPONSABLE global. El rol dentro de cada equipo se gestiona en /api/teams.")
    @PreAuthorize("hasRole('RESPONSABLE')")
    @PatchMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable Long id, @Valid @RequestBody UpdateGlobalRoleRequest request) {
        return userService.updateGlobalRole(id, request);
    }

    @Operation(summary = "Deshabilitar una cuenta",
            description = "Baja logica: la cuenta deja de poder acceder pero se conserva su historial.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Cuenta deshabilitada"))
    @PreAuthorize("hasRole('RESPONSABLE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        userService.disable(id);
        return ResponseEntity.noContent().build();
    }
}
