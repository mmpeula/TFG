package com.tfg.kanban.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitario puro (sin contexto de Spring ni Docker) que valida el andamiaje JWT.
 * Sirve tambien de plantilla para los futuros tests con JUnit 5 + Mockito.
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("secreto-de-pruebas-con-al-menos-32-caracteres");
        properties.setExpirationMinutes(60);
        properties.setIssuer("tfg-kanban-test");
        jwtService = new JwtService(properties);
    }

    @Test
    @DisplayName("Un token generado se valida y conserva subject, rol e id de usuario")
    void generatesAndParsesToken() {
        String token = jwtService.generateToken("alumno@uni.es", 42L, "MIEMBRO");

        Optional<Claims> claims = jwtService.parse(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("alumno@uni.es");
        assertThat(claims.get().get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("MIEMBRO");
        assertThat(claims.get().get(JwtService.CLAIM_USER_ID, Number.class).longValue()).isEqualTo(42L);
    }

    @Test
    @DisplayName("Un token manipulado se rechaza")
    void rejectsTamperedToken() {
        String token = jwtService.generateToken("alumno@uni.es", 1L, "RESPONSABLE");

        assertThat(jwtService.isValid(token + "x")).isFalse();
        assertThat(jwtService.isValid("esto.no.es.un.jwt")).isFalse();
    }
}
