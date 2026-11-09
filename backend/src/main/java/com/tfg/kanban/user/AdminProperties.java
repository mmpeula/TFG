package com.tfg.kanban.user;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    /** Cuenta que {@link AdminBootstrapRunner} crea si todavia no hay ningun ADMIN. */
    private String email = "admin@planifia.local";

    /** Sin valor por defecto: una clave conocida por todos no protege nada. Sin ella no se crea la cuenta. */
    private String password;
}
