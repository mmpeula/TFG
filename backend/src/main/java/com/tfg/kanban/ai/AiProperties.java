package com.tfg.kanban.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuracion del modulo de IA. PlanifIA habla con un modelo alojado en Groq;
 * el documento y los datos del equipo que se mandan en el prompt salen de esta
 * maquina hacia sus servidores para generar el plan.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Si es false, el endpoint responde 503 explicando que la IA esta apagada. */
    private boolean enabled = true;

    /** URL de la API de Groq, compatible con el formato de OpenAI. */
    private String baseUrl = "https://api.groq.com/openai/v1";

    /** Clave de API de Groq. Sin ella no se puede hablar con el modelo. */
    private String apiKey = "";

    /** Modelo a usar, de los que sirve Groq (ver console.groq.com/docs/models). */
    private String model = "openai/gpt-oss-120b";

    /** Groq responde rapido (hardware LPU), pero un documento largo con varios sprints puede tardar. */
    private Duration timeout = Duration.ofMinutes(2);

    /**
     * Tope de seguridad de cuantas tareas se aceptan del plan completo: un corte
     * por si el documento tiene muchos sprints y la suma se dispara.
     */
    private int maxTasks = 60;

    /** Temperatura baja: se busca un reparto consistente, no creatividad. */
    private double temperature = 0.2;

    /**
     * Cuantas veces se repite la llamada antes de darlo por perdido: si la
     * respuesta no es JSON valido, o si Groq ha limitado la peticion por su cuota
     * de tokens por minuto (en ese caso se espera antes de cada reintento lo que la
     * propia API dice, hasta 30 s). Con 2 caben incluso dos rachas de espera
     * seguidas sin fallar, que es lo que tarda la cuota en recuperarse si se pide
     * generar un plan varias veces seguidas en menos de un minuto.
     */
    private int maxRetries = 2;

    /**
     * Tope de tokens que el modelo puede generar en una sola llamada (max_tokens),
     * cuando el presupuesto de la cuenta lo permite. Groq permite hasta 65536 en
     * este modelo, pero esa cifra es la del modelo, no la de la cuenta: el tier
     * gratuito ("on_demand") limita a solo 8000 tokens por minuto entre el prompt y
     * la respuesta juntos. {@link GroqClient} calcula en cada llamada cuanto cabe
     * de verdad segun el tamano del prompt y nunca pide mas que eso; este valor es
     * solo el techo para cuando el prompt es pequeno. Con una cuenta de pago (Dev
     * Tier) se puede subir sin que la cuota sea el problema.
     */
    private int maxOutputTokens = 6_000;
}
