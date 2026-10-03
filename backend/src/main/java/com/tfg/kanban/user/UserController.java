package com.tfg.kanban.user;

import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.user.dto.UpdateGlobalRoleRequest;
import com.tfg.kanban.user.dto.UpdateUserRequest;
import com.tfg.kanban.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Consulta y mantenimiento de cuentas")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Buscar usuarios",
            description = "Listado paginado con busqueda por nombre o email. Reservado al administrador "
                    + "de la plataforma: es el directorio completo de cuentas y lo consume su panel. "
                    + "Para meter a alguien en un equipo no hace falta: se le invita por su email, que "
                    + "quien invita ya conoce.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<UserResponse> search(
            @Parameter(description = "Texto a buscar en nombre o email") @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.search(search, pageable);
    }

    @Operation(summary = "Actualizar el perfil",
            description = "Solo el propio usuario o el administrador de la plataforma pueden modificar una cuenta.")
    @PreAuthorize("@access.isSelf(#id) or hasRole('ADMIN')")
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @Operation(summary = "Cambiar el rol global de un usuario",
            description = "Reservado al administrador de la plataforma. Los cambios son siempre hacia arriba: "
                    + "alumno a docente o a administrador, y docente a administrador. Un docente no vuelve a "
                    + "ser alumno ni un administrador se degrada. Es un rol independiente de los equipos; "
                    + "el rol dentro de cada equipo se gestiona en /api/teams.")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable Long id, @Valid @RequestBody UpdateGlobalRoleRequest request) {
        return userService.updateGlobalRole(id, request);
    }

    @Operation(summary = "Deshabilitar una cuenta",
            description = "Baja logica: la cuenta deja de poder acceder pero se conserva su historial. "
                    + "No se puede dar de baja al unico administrador habilitado.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Cuenta deshabilitada"))
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        userService.disable(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivar una cuenta deshabilitada")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Cuenta reactivada"))
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/enable")
    public ResponseEntity<Void> enable(@PathVariable Long id) {
        userService.enable(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Subir la foto de perfil",
            description = "Solo el propio usuario o el administrador. Peticion multipart/form-data con el "
                    + "campo 'file'; sustituye la foto anterior si habia una.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(type = "object", requiredProperties = "file")))
    @PreAuthorize("@access.isSelf(#id) or hasRole('ADMIN')")
    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return userService.uploadAvatar(id, file);
    }

    @Operation(summary = "Descargar la foto de perfil",
            description = "El propio usuario, el administrador, o cualquiera que comparta un equipo con el.")
    @PreAuthorize("@access.isSelf(#id) or @access.sharesTeamWith(#id) or hasRole('ADMIN')")
    @GetMapping("/{id}/avatar")
    public ResponseEntity<Resource> downloadAvatar(@PathVariable Long id) {
        UserService.AvatarFile avatar = userService.loadAvatar(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        avatar.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : avatar.contentType())
                .body(avatar.resource());
    }

    @Operation(summary = "Quitar la foto de perfil",
            description = "Solo el propio usuario o el administrador. Vuelve a mostrar las iniciales.")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "Foto eliminada"))
    @PreAuthorize("@access.isSelf(#id) or hasRole('ADMIN')")
    @DeleteMapping("/{id}/avatar")
    public ResponseEntity<Void> removeAvatar(@PathVariable Long id) {
        userService.removeAvatar(id);
        return ResponseEntity.noContent().build();
    }
}
