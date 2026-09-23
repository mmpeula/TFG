package com.tfg.kanban.user;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.user.dto.UpdateGlobalRoleRequest;
import com.tfg.kanban.user.dto.UpdateUserRequest;
import com.tfg.kanban.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PageResponse<UserResponse> search(String search, Pageable pageable) {
        String term = StringUtils.hasText(search) ? search.trim() : "";
        return PageResponse.of(
                userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        term, term, pageable),
                UserResponse::from);
    }

    public UserResponse findById(Long id) {
        return UserResponse.from(getUser(id));
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

    @Transactional
    public UserResponse updateGlobalRole(Long id, UpdateGlobalRoleRequest request) {
        User user = getUser(id);
        if (!isAccountRole(request.role())) {
            throw new BusinessRuleException("Una cuenta solo puede ser ADMIN, DOCENTE o MIEMBRO");
        }
        if (user.getRole() == Role.ADMIN && request.role() != Role.ADMIN) {
            ensureNotLastAdmin();
        }
        user.setRole(request.role());
        return UserResponse.from(user);
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
        if (user.getRole() == Role.ADMIN) {
            ensureNotLastAdmin();
        }
        user.setEnabled(false);
    }

    /** Da de alta de nuevo una cuenta deshabilitada. */
    @Transactional
    public void enable(Long id) {
        getUser(id).setEnabled(true);
    }

    private void ensureNotLastAdmin() {
        if (userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("La plataforma debe tener al menos un administrador");
        }
    }

    private boolean isAccountRole(Role role) {
        return role == Role.ADMIN || role == Role.DOCENTE || role == Role.MIEMBRO;
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }
}
