package com.tfg.kanban.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuracion del modulo de IA. PlanifIA habla con un modelo alojado en
 * DeepSeek; el documento y los datos del equipo que se mandan en el prompt
 * salen de esta maquina hacia sus servidores para generar el plan.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Si es false, el endpoint responde 503 explicando que la IA esta apagada. */
    private boolean enabled = true;

    /** URL de la API de DeepSeek, compatible con el formato de OpenAI. */
    private String baseUrl = "https://api.deepseek.com";

    /** Clave de API de DeepSeek. Sin ella no se puede hablar con el modelo. */
    private String apiKey = "";

    /** Modelo a usar, de los que sirve DeepSeek (ver api-docs.deepseek.com). */
    private String model = "deepseek-flash";

    /**
     * El plan entero, con todos sus sprints, sale de una sola respuesta: con un
     * documento largo y un equipo grande son varios miles de tokens de salida.
     */
    private Duration timeout = Duration.ofMinutes(5);

    /**
     * Tope de seguridad de cuantas tareas se aceptan del plan completo: un corte
     * por si el documento tiene muchos sprints y la suma se dispara.
     */
    private int maxTasks = 60;

    /** Temperatura baja: se busca un reparto consistente, no creatividad. */
    private double temperature = 0.2;

    /**
     * Tope de tokens que el modelo puede generar en una sola respuesta
     * (max_tokens). Tiene que caber el plan completo de todos los sprints de una
     * vez; DeepSeek solo cobra lo que de verdad genera, asi que un techo alto no
     * cuesta nada si el plan es corto, y uno bajo corta el JSON a medias.
     */
    private int maxOutputTokens = 32_000;

    /**
     * Tope de tokens de una respuesta del asistente de chat. Mucho mas bajo que
     * {@link #maxOutputTokens}: el propio prompt le pide respuestas de 120
     * palabras salvo que hagan falta pasos o un fragmento de codigo, y sin techo
     * propio una respuesta que se desmadra pagaria (y esperaria) como si fuera
     * generando un plan entero.
     */
    private int chatMaxOutputTokens = 1_500;
}
