package com.tfg.kanban.team;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Ajustes del correo que manda la aplicacion. Ver {@code application.yml}. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    /** Con esto a false no se manda nada: el correo solo se escribe en el log. */
    private boolean enabled = true;

    /** Remitente de los avisos. */
    private String from = "no-reply@planifia.local";

    /** Donde vive la interfaz, para componer el enlace de la invitacion. */
    private String baseUrl = "http://localhost:3000";
}
