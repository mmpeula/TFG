package com.tfg.kanban.security;

import com.tfg.kanban.common.BusinessRuleException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AppUserDetails> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserDetails principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static Optional<Long> currentUserId() {
        return currentUser().map(AppUserDetails::getId);
    }

    /**
     * El id del usuario autenticado, para los servicios que solo se alcanzan con
     * sesion y para los que no tenerla es un fallo, no un caso a contemplar.
     *
     * <p>Vive aqui y no repetido en cada servicio porque si no cada uno elegia su
     * propia excepcion y la misma situacion imposible salia unas veces como 409 y
     * otras como 500.</p>
     */
    public static Long requireCurrentUserId() {
        return currentUserId()
                .orElseThrow(() -> new BusinessRuleException("No hay usuario autenticado"));
    }
}
