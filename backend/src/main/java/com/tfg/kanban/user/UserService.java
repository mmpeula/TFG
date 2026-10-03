package com.tfg.kanban.user;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.storage.FileStorageService;
import com.tfg.kanban.user.dto.UpdateGlobalRoleRequest;
import com.tfg.kanban.user.dto.UpdateUserRequest;
import com.tfg.kanban.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public PageResponse<UserResponse> search(String search, Pageable pageable) {
        String term = StringUtils.hasText(search) ? search.trim() : "";
        return PageResponse.of(
                userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        term, term, pageable),
                UserResponse::from);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = getUser(id);
        user.setFullName(request.fullName().trim());
        if (StringUtils.hasText(request.password())) {
            ensureMayChangePassword(user, request.currentPassword());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(user);
    }

    /**
     * Para cambiarse uno mismo la contrasena hay que saber la de ahora.
     *
     * <p>Sin esto bastaba con una sesion abierta y sin vigilancia —un portatil de
     * la biblioteca -- para dejar a su dueno fuera de su propia cuenta: la nueva
     * contrasena la elige quien esta sentado delante y el dueno ya no la sabe.</p>
     *
     * <p>Al administrador que restablece la de otra persona no se le pide, porque
     * no la tiene: eso es precisamente para lo que existe el restablecimiento. La
     * suya propia si la tiene que saber, como todo el mundo.</p>
     */
    private void ensureMayChangePassword(User user, String currentPassword) {
        boolean esSuPropiaCuenta = SecurityUtils.currentUserId()
                .filter(userId -> userId.equals(user.getId()))
                .isPresent();
        if (!esSuPropiaCuenta) {
            return;
        }
        if (!StringUtils.hasText(currentPassword)
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("La contrasena actual no es correcta");
        }
    }

    /**
     * Los cambios de rol son siempre hacia arriba: alumno puede pasar a docente o a
     * administrador, y un docente a administrador.
     *
     * <p>No hay vuelta atras: un docente no vuelve a ser alumno ni un administrador
     * se degrada. Asi nunca se pierde un rol por un clic, y la plataforma no puede
     * quedarse sin nadie que la administre por este camino (por el de dar de baja,
     * vease {@link #disable}). Pedir el rol que la cuenta ya tiene no cambia nada.</p>
     */
    @Transactional
    public UserResponse updateGlobalRole(Long id, UpdateGlobalRoleRequest request) {
        User user = getUser(id);
        Role target = request.role();
        if (target == Role.RESPONSABLE) {
            throw new BusinessRuleException("RESPONSABLE es un rol de equipo, no de cuenta");
        }
        if (!isPromotion(user.getRole(), target)) {
            throw new BusinessRuleException(switch (user.getRole()) {
                case ADMIN -> "Un administrador no se puede degradar a otro rol";
                case DOCENTE -> "Un docente solo puede pasar a administrador: no puede volver a ser alumno";
                default -> "Una cuenta de alumno solo puede pasar a docente o a administrador";
            });
        }
        user.setRole(target);
        return UserResponse.from(user);
    }

    /** Mismo rol (no cambia nada) o uno de los saltos permitidos: alumno -> docente -> administrador. */
    private static boolean isPromotion(Role current, Role target) {
        return switch (current) {
            case ADMIN -> target == Role.ADMIN;
            case DOCENTE -> target == Role.DOCENTE || target == Role.ADMIN;
            // Alumno: puede pasar a docente o a administrador.
            default -> true;
        };
    }

    /**
     * Baja logica: se deshabilita la cuenta en lugar de borrarla, para no perder
     * la autoria de tareas, comentarios y registros de actividad.
     */
    @Transactional
    public void disable(Long id) {
        if (SecurityUtils.currentUserId().filter(id::equals).isPresent()) {
            throw new BusinessRuleException("No puedes deshabilitar tu propia cuenta");
        }
        User user = getUser(id);
        if (user.getRole() == Role.ADMIN && user.isEnabled()) {
            ensureNotLastAdmin();
        }
        user.setEnabled(false);
    }

    /** Da de alta de nuevo una cuenta deshabilitada. */
    @Transactional
    public void enable(Long id) {
        getUser(id).setEnabled(true);
    }

    /** Sustituye la foto de perfil, borrando la anterior si habia una. */
    @Transactional
    public UserResponse uploadAvatar(Long id, MultipartFile file) {
        User user = getUser(id);
        FileStorageService.StoredImage image = fileStorageService.storeImage(file);
        String previous = user.getAvatarStoredName();
        user.setAvatarStoredName(image.storedName());
        user.setAvatarContentType(image.contentType());
        if (previous != null) {
            fileStorageService.delete(previous);
        }
        return UserResponse.from(user);
    }

    /** Quita la foto de perfil; el usuario vuelve a mostrar sus iniciales. */
    @Transactional
    public void removeAvatar(Long id) {
        User user = getUser(id);
        String previous = user.getAvatarStoredName();
        if (previous == null) {
            return;
        }
        user.setAvatarStoredName(null);
        user.setAvatarContentType(null);
        fileStorageService.delete(previous);
    }

    public AvatarFile loadAvatar(Long id) {
        User user = getUser(id);
        if (user.getAvatarStoredName() == null) {
            throw new ResourceNotFoundException("Este usuario no tiene foto de perfil");
        }
        Resource resource = fileStorageService.loadAsResource(user.getAvatarStoredName());
        return new AvatarFile(resource, user.getAvatarContentType());
    }

    /** Contenido de la foto de perfil junto con su tipo MIME, en una sola lectura del usuario. */
    public record AvatarFile(Resource resource, String contentType) {
    }

    /** Quedaria sin ningun administrador que pueda entrar: se cuentan solo los habilitados. */
    private void ensureNotLastAdmin() {
        if (userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("La plataforma debe tener al menos un administrador");
        }
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }
}
