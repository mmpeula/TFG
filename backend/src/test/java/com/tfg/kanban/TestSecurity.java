package com.tfg.kanban;

import com.tfg.kanban.security.AppUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Utilidad para simular un usuario autenticado en los tests unitarios. */
public final class TestSecurity {

    private TestSecurity() {
    }

    public static void authenticateAs(Long userId, String role) {
        AppUserDetails principal = new AppUserDetails(userId, "test-" + userId + "@uni.es", "ROLE_" + role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
