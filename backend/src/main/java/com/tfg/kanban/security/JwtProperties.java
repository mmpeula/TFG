package com.tfg.kanban.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuracion del JWT. Los valores llegan desde application.yml y,
 * en el caso del secreto, siempre desde una variable de entorno.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /** Clave HMAC-SHA en Base64 o texto plano. Minimo 32 bytes para HS256. */
    @NotBlank
    private String secret;

    /** Minutos de validez del access token. */
    private long expirationMinutes = 120;

    /** Emisor incluido en el claim "iss". */
    private String issuer = "tfg-kanban";
}
