package com.tfg.kanban;

import com.tfg.kanban.domain.model.Role;
import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.security.AppUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Utilidad para simular un usuario autenticado en los tests unitarios. */
public final class TestSecurity {

    private TestSecurity() {
    }

    public static void authenticateAs(Long userId, String role) {
        User user = User.builder()
                .id(userId)
                .email("test-" + userId + "@uni.es")
                .passwordHash("")
                .role(Role.valueOf(role))
                .enabled(true)
                .build();
        AppUserDetails principal = new AppUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
