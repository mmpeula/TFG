package com.tfg.kanban.security;

import com.tfg.kanban.TestSecurity;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** El filtro nunca debe cortar la cadena: solo rellena el contexto si el token vale. */
class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("secreto-de-pruebas-con-al-menos-32-caracteres");
        properties.setIssuer("tfg-kanban-test");
        jwtService = new JwtService(properties);
        filter = new JwtAuthenticationFilter(jwtService);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    private void run(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
    }

    @Test
    @DisplayName("Con un token valido deja la autenticacion en el contexto")
    void authenticatesWithValidToken() throws Exception {
        String token = jwtService.generateToken("ana@uni.es", 3L, "RESPONSABLE");

        run("Bearer " + token);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_RESPONSABLE");
        assertThat(((AppUserDetails) authentication.getPrincipal()).getId()).isEqualTo(3L);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("Sin cabecera Authorization la peticion sigue sin autenticar")
    void continuesWithoutHeader() throws Exception {
        run(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("Una cabecera que no es Bearer se ignora")
    void ignoresNonBearerHeader() throws Exception {
        run("Basic dXN1YXJpbzpjbGF2ZQ==");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("Un token invalido no autentica pero tampoco corta la cadena")
    void ignoresInvalidToken() throws Exception {
        run("Bearer esto.no.es.un.jwt");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("Si ya hay autenticacion en el contexto, el filtro no la pisa")
    void doesNotOverrideExistingAuthentication() throws Exception {
        TestSecurity.authenticateAs(99L, "DOCENTE");
        String token = jwtService.generateToken("otra@uni.es", 1L, "RESPONSABLE");

        run("Bearer " + token);

        var principal = (AppUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getId()).isEqualTo(99L);
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
