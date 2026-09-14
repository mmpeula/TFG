package com.tfg.kanban.security;

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
}
