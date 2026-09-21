package com.tfg.kanban.team;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Manda los correos de la aplicacion.
 *
 * <p>Nunca tumba la operacion que lo llama: si no hay servidor de correo
 * configurado, o si falla, se deja constancia en el log y se sigue. Una
 * invitacion que ya esta guardada no debe perderse porque el SMTP este caido;
 * la persona invitada la ve igualmente al entrar en la aplicacion, que es el
 * camino que siempre funciona.</p>
 *
 * <p>En desarrollo el destino es Mailpit (ver {@code docker-compose.yml}), que
 * recoge todo lo que se envia y lo ensena en {@code http://localhost:8025} sin
 * mandar nada de verdad a nadie.</p>
 */
@Slf4j
@Service
public class MailService {

    private final MailProperties properties;
    /** ObjectProvider y no inyeccion directa: sin servidor configurado, este bean no existe. */
    private final ObjectProvider<JavaMailSender> sender;

    public MailService(MailProperties properties, ObjectProvider<JavaMailSender> sender) {
        this.properties = properties;
        this.sender = sender;
    }

    /** @return true si el correo ha salido de verdad. */
    public boolean send(String to, String subject, String body) {
        JavaMailSender mailSender = properties.isEnabled() ? sender.getIfAvailable() : null;
        if (mailSender == null) {
            log.info("Correo no enviado (sin servidor configurado). Para: {} | Asunto: {}\n{}", to, subject, body);
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Correo enviado a {} | Asunto: {}", to, subject);
            return true;
        } catch (Exception ex) {
            log.warn("No se ha podido enviar el correo a {}: {}", to, ex.getMessage());
            return false;
        }
    }

    public String getBaseUrl() {
        return properties.getBaseUrl();
    }
}
