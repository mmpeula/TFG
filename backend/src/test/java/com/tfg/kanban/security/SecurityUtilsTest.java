package com.tfg.kanban.security;

import com.tfg.kanban.TestSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("Devuelve el usuario autenticado y su id")
    void returnsCurrentUser() {
        TestSecurity.authenticateAs(7L, "MIEMBRO");

        assertThat(SecurityUtils.currentUserId()).contains(7L);
        assertThat(SecurityUtils.currentUser()).isPresent();
        assertThat(SecurityUtils.currentUser().orElseThrow().getUsername()).isEqualTo("test-7@uni.es");
    }

    @Test
    @DisplayName("Sin contexto de seguridad no hay usuario")
    void emptyWithoutAuthentication() {
        SecurityContextHolder.clearContext();

        assertThat(SecurityUtils.currentUser()).isEmpty();
        assertThat(SecurityUtils.currentUserId()).isEmpty();
    }

    @Test
    @DisplayName("Un principal que no es de la aplicacion se ignora")
    void ignoresForeignPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("clave", "anonimo",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(SecurityUtils.currentUser()).isEmpty();
    }
}
