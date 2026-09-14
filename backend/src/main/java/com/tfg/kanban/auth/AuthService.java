package com.tfg.kanban.auth;

import com.tfg.kanban.auth.dto.AuthResponse;
import com.tfg.kanban.auth.dto.LoginRequest;
import com.tfg.kanban.auth.dto.RegisterRequest;
import com.tfg.kanban.common.BusinessRuleException;
import com.tfg.kanban.common.ResourceNotFoundException;
import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.UserRepository;
import com.tfg.kanban.security.JwtService;
import com.tfg.kanban.security.SecurityUtils;
import com.tfg.kanban.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Ya existe un usuario con el email " + email);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                // El rol global NUNCA lo elige quien se registra: solo lo cambia despues un
                // RESPONSABLE global desde PATCH /api/users/{id}/role. Aceptarlo aqui dejaba
                // que cualquiera se diera de alta como RESPONSABLE y, con ese token, cambiara
                // el rol o la contrasena de cualquier otra cuenta.
                .role(Role.MIEMBRO)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Usuario registrado: {} ({})", saved.getEmail(), saved.getRole());
        return buildResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (DisabledException ex) {
            throw new BusinessRuleException("La cuenta esta deshabilitada");
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);
        return buildResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new InvalidCredentialsException());
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
    }

    private AuthResponse buildResponse(User user) {
        String token = jwtService.generateToken(user.getEmail(), user.getId(), user.getRole().name());
        return AuthResponse.of(token, jwtService.getExpirationMinutes(), UserResponse.from(user));
    }
}
