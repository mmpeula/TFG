package com.tfg.kanban.security;

import com.tfg.kanban.domain.model.User;
import com.tfg.kanban.domain.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Lee el token del cabecera {@code Authorization: Bearer <token>} y, si es valido y la
 * cuenta sigue habilitada, deja la autenticacion en el {@link SecurityContextHolder} con
 * el rol que la cuenta tiene <b>ahora</b>, no con el que tenia al emitirse el token.
 *
 * No corta la cadena si no hay token: de eso se encarga la configuracion de
 * autorizacion de {@link SecurityConfig}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parse(token).ifPresent(claims -> authenticate(request, claims));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, Claims claims) {
        String role = claims.get(JwtService.CLAIM_ROLE, String.class);
        Number userId = claims.get(JwtService.CLAIM_USER_ID, Number.class);

        // Firma valida pero sin rol o sin id no es una sesion utilizable: toda la
        // autorizacion cuelga de esos dos claims. Se deja pasar sin autenticar, que
        // acaba en 401, en lugar de montar un principal con "ROLE_null" y un id
        // nulo que luego revienta en cualquier comprobacion de permisos.
        if (role == null || userId == null) {
            log.debug("Token JWT sin rol o sin id de usuario: se ignora");
            return;
        }

        // El token dice quien es; lo que puede hacer ahora lo dice la base de datos. Si no,
        // una cuenta dada de baja seguiria entrando hasta que caducara su token (horas), y un
        // cambio de rol no valdria hasta volver a iniciar sesion. Es una lectura por clave
        // primaria por peticion: barata a esta escala.
        Optional<User> user = userRepository.findById(userId.longValue());
        if (user.isEmpty() || !user.get().isEnabled()) {
            log.debug("Token JWT de una cuenta inexistente o deshabilitada: se ignora");
            return;
        }
        AppUserDetails principal = new AppUserDetails(user.get());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
