package com.tfg.kanban.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente de Groq: el modelo corre en su nube, no en esta maquina.
 *
 * <p>Usa el endpoint compatible con OpenAI {@code /chat/completions}, con
 * {@code stream: false} porque interesa una respuesta unica y estructurada, no
 * un flujo de texto. Al no ser local, el documento y los datos del equipo que se
 * mandan en el prompt salen de la maquina del usuario hacia los servidores de
 * Groq; eso es lo que gana en velocidad (su hardware LPU es mucho mas rapido que
 * cualquier GPU domestica) frente a lo que se pierde en no ser 100% local.</p>
 */
@Slf4j
@Component
public class GroqClient {

    /** En el chat interesa algo mas de soltura que al generar JSON, pero sin inventar. */
    private static final double CHAT_TEMPERATURE = 0.4;

    /** Lo que se espera a que el servidor de Groq coja el telefono, no a que conteste. */
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    /**
     * Cuota real de esta cuenta de Groq ("on_demand", tier gratuito): 8000 tokens
     * por minuto entre el prompt y la respuesta juntos (verificado contra la API,
     * cabecera {@code x-ratelimit-limit-tokens}). Es el limite de la cuenta, no del
     * modelo: se ha comprobado igual con openai/gpt-oss-120b y con
     * openai/gpt-oss-20b (el que se usa ahora), y pedir mas de esto en una sola
     * llamada la rechaza siempre, la reciba o no.
     */
    private static final int TOKEN_BUDGET_PER_MINUTE = 8_000;

    /** Margen bajo la cuota, para no ir siempre al limite exacto por como se estima el prompt. */
    private static final int TOKEN_BUDGET_SAFETY_MARGIN = 500;

    /** Por debajo de esto no compensa ni intentarlo: no cabe ni un JSON minimo. */
    private static final int MIN_OUTPUT_TOKENS = 1_000;

    /** Conversion aproximada caracter/token para textos en espanol e ingles tecnico. */
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    private static final Pattern RETRY_AFTER_PATTERN = Pattern.compile("try again in ([0-9]+(?:\\.[0-9]+)?)s");

    /** Si Groq no dice cuanto esperar, este es el margen prudente por defecto. */
    private static final double DEFAULT_RETRY_AFTER_SECONDS = 5.0;

    /** Nunca se espera mas que esto: mejor devolver el error que colgar la peticion. */
    private static final double MAX_RETRY_AFTER_SECONDS = 30.0;

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GroqClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        factory.setReadTimeout((int) Math.min(Integer.MAX_VALUE, properties.getTimeout().toMillis()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .requestFactory(factory)
                .build();
    }

    /** Devuelve el contenido del mensaje del modelo, que se espera que sea JSON. */
    public String chatJson(String systemPrompt, String userPrompt) {
        return chatJson(systemPrompt, List.of(new Message("user", userPrompt)));
    }

    /**
     * Igual, pero con varios turnos: es como se le pide que corrija un plan, con
     * su respuesta anterior y los errores que tiene a continuacion.
     */
    public String chatJson(String systemPrompt, List<Message> messages) {
        return chat(systemPrompt, messages, true, properties.getTemperature());
    }

    /**
     * Conversacion en texto libre: la usa el asistente para responder preguntas
     * sobre el workspace. Se le pasa el historial para que entienda «y eso?».
     */
    public String chatText(String systemPrompt, List<Message> messages) {
        return chat(systemPrompt, messages, false, CHAT_TEMPERATURE);
    }

    /** Un turno de la conversacion. {@code role} es "user" o "assistant". */
    public record Message(String role, String content) {
    }

    /**
     * Groq ha limitado la peticion por la cuota de tokens por minuto (429), pero
     * dice cuanto tarda en haber hueco otra vez: esperar y reintentar una vez suele
     * bastar, a diferencia de un 413 (la peticion en si pide mas que la cuota
     * entera, y esperar no cambia eso).
     */
    @Getter
    public static class RateLimitedException extends AiUnavailableException {
        private final double retryAfterSeconds;

        RateLimitedException(String message, double retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    private String chat(String systemPrompt, List<Message> messages, boolean json, double temperature) {
        List<Map<String, Object>> turns = new ArrayList<>();
        turns.add(Map.of("role", "system", "content", systemPrompt));
        for (Message message : messages) {
            turns.add(Map.of("role", message.role(), "content", message.content()));
        }

        Map<String, Object> body = new LinkedHashMap<>(Map.of(
                "model", properties.getModel(),
                "stream", false,
                "temperature", temperature,
                // Acota lo peor: sin el formato JSON forzado, el modelo podria no
                // encontrar el token de fin y seguir generando sin parar. Con esto,
                // como mucho corta a mitad de un JSON, que ya se trata como respuesta
                // invalida y se reintenta. El numero se calcula por peticion (ver
                // maxTokensDentroDeLaCuota): un valor fijo o se pasa de la cuota con
                // un prompt grande, o desperdicia margen con uno pequeno.
                "max_tokens", maxTokensDentroDeLaCuota(systemPrompt, turns),
                "messages", turns));
        if (json) {
            body.put("response_format", Map.of("type", "json_object"));
            // Este modelo "razona" antes de escribir la respuesta, y ese razonamiento
            // sale de la misma bolsa de max_tokens: sin bajarlo, puede consumir buena
            // parte del tope pensando y dejar el JSON a medias. El plan ya viene con
            // los pasos y las reglas explicitos en el prompt de sistema, asi que no
            // hace falta que la parte de razonar sea larga.
            body.put("reasoning_effort", "low");
        }

        try {
            String response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new AiUnavailableException("El modelo no ha devuelto ninguna respuesta");
            }
            return objectMapper.readTree(response).path("choices").path(0).path("message").path("content").asText();

        } catch (AiUnavailableException ex) {
            // Ya lleva un mensaje util: no hay que taparlo con el generico de abajo.
            throw ex;

        } catch (ResourceAccessException ex) {
            log.warn("No se ha podido contactar con Groq en {}: {}", properties.getBaseUrl(), ex.getMessage());
            throw new AiUnavailableException(
                    "No se ha podido contactar con Groq en %s. Comprueba la conexion a internet."
                            .formatted(properties.getBaseUrl()), ex);
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            String detalle = errorDeGroq(ex.getResponseBodyAsString());
            log.warn("Groq ha respondido con error {}: {}", status, ex.getResponseBodyAsString());
            String mensaje = mensajeParaError(status, detalle);
            if (status == 429) {
                throw new RateLimitedException(mensaje, retryAfterSecondsDe(detalle));
            }
            throw new AiUnavailableException(mensaje, ex);
        } catch (RestClientException ex) {
            log.warn("Groq ha respondido con error: {}", ex.getMessage());
            throw new AiUnavailableException(
                    "Groq ha respondido con un error. Revisa la API key y que el modelo '%s' exista."
                            .formatted(properties.getModel()), ex);
        } catch (Exception ex) {
            throw new AiUnavailableException("Respuesta de Groq ilegible", ex);
        }
    }

    /**
     * Cuanto se le pide al modelo que genere, sin que el prompt mas la respuesta
     * se pasen de la cuota de la cuenta ({@link #TOKEN_BUDGET_PER_MINUTE}). Se
     * estima el prompt por caracteres (no hay forma de contar tokens exactos sin
     * el tokenizador del modelo), con margen de sobra para no quedarse corto.
     */
    private int maxTokensDentroDeLaCuota(String systemPrompt, List<Map<String, Object>> turns) {
        int caracteresPrompt = systemPrompt.length();
        for (Map<String, Object> turn : turns) {
            caracteresPrompt += String.valueOf(turn.get("content")).length();
        }
        int tokensPromptEstimados = caracteresPrompt / CHARS_PER_TOKEN_ESTIMATE;
        int disponibles = TOKEN_BUDGET_PER_MINUTE - tokensPromptEstimados - TOKEN_BUDGET_SAFETY_MARGIN;
        return Math.max(MIN_OUTPUT_TOKENS, Math.min(properties.getMaxOutputTokens(), disponibles));
    }

    /**
     * El mensaje de "error.message" del cuerpo de Groq, o el cuerpo entero si no se
     * puede leer como tal. Puede venir vacio: para un 401, el HttpURLConnection de
     * Java intercepta la respuesta para su propia negociacion de autenticacion y a
     * veces no deja leer el cuerpo del error.
     */
    private String errorDeGroq(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "";
        try {
            String mensaje = objectMapper.readTree(responseBody).path("error").path("message").asText();
            return mensaje.isBlank() ? responseBody : mensaje;
        } catch (Exception ex) {
            return responseBody;
        }
    }

    /** Groq dice "please try again in 16.49s" en el propio mensaje del 429: se usa eso, no una espera a ciegas. */
    private double retryAfterSecondsDe(String detalleGroq) {
        Matcher matcher = RETRY_AFTER_PATTERN.matcher(detalleGroq);
        double segundos = matcher.find() ? Double.parseDouble(matcher.group(1)) : DEFAULT_RETRY_AFTER_SECONDS;
        return Math.min(segundos, MAX_RETRY_AFTER_SECONDS);
    }

    private String mensajeParaError(int status, String detalleGroq) {
        String detalle = detalleGroq.isBlank() ? "" : ": " + detalleGroq;
        return switch (status) {
            case 401, 403 -> "Groq ha rechazado la API key. Revisa GROQ_API_KEY" + detalle;
            case 404 -> "El modelo '%s' no existe en Groq o no esta disponible para esta cuenta%s"
                    .formatted(properties.getModel(), detalle);
            case 429, 413 -> "Groq ha limitado la peticion por su cuota de tokens por minuto" + detalle
                    + ". Prueba con un documento mas corto o espera un minuto antes de reintentar.";
            default -> "Groq ha respondido con un error (%d)%s".formatted(status, detalle);
        };
    }
}
