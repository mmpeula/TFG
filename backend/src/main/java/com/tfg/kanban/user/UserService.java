package com.tfg.kanban.user;

import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.PageResponse;
import com.tfg.kanban.common.ResourceNotFoundException;
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
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateGlobalRole(Long id, UpdateGlobalRoleRequest request) {
        User user = getUser(id);
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
        user.setEnabled(false);
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }
}
