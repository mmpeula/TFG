package com.tfg.kanban.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * Generacion y validacion de tokens JWT (HS256).
 *
 * El token lleva el email del usuario en el subject y su id y su rol en claims. El
 * filtro solo se fia del id: lo que la cuenta puede hacer lo vuelve a leer de la base
 * de datos en cada peticion (ver {@link JwtAuthenticationFilter}), para que una baja o
 * un cambio de rol valgan al momento y no cuando caduque el token.
 */
@Slf4j
@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_USER_ID = "uid";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, Long userId, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getExpirationMinutes() * 60);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(email)
                .claims(Map.of(CLAIM_ROLE, role, CLAIM_USER_ID, userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /** Devuelve los claims si el token es valido; {@code Optional.empty()} en caso contrario. */
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token JWT rechazado: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public long getExpirationMinutes() {
        return properties.getExpirationMinutes();
    }
}
