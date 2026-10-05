package com.tfg.kanban.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Cliente de DeepSeek: el modelo corre en su nube, no en esta maquina.
 *
 * <p>Usa el endpoint compatible con OpenAI {@code /chat/completions}, con
 * {@code stream: false} porque interesa una respuesta unica y estructurada, no
 * un flujo de texto. El documento y los datos del equipo que se mandan en el
 * prompt salen de la maquina del usuario hacia los servidores de DeepSeek.</p>
 */
@Slf4j
@Component
public class DeepSeekClient {

    /** En el chat interesa algo mas de soltura que al generar JSON, pero sin inventar. */
    private static final double CHAT_TEMPERATURE = 0.4;

    /** Leer una imagen es transcribir lo que se ve: nada de soltura. */
    private static final double VISION_TEMPERATURE = 0.1;

    /** Lo que se espera a que el servidor de DeepSeek coja el telefono, no a que conteste. */
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DeepSeekClient(AiProperties properties, ObjectMapper objectMapper) {
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

    /** Una peticion y su respuesta, que se espera que sea JSON. */
    public String chatJson(String systemPrompt, String userPrompt) {
        return chatJson(systemPrompt, List.of(new Message("user", userPrompt)));
    }

    /**
     * Igual, pero con varios turnos: es como se le pide que corrija un JSON mal
     * formado, con su respuesta anterior y el error de parseo a continuacion.
     */
    public String chatJson(String systemPrompt, List<Message> messages) {
        return chat(systemPrompt, messages, true, properties.getTemperature(), properties.getMaxOutputTokens());
    }

    /**
     * Conversacion en texto libre: la usa el asistente para responder preguntas
     * sobre el workspace. Se le pasa el historial para que entienda «y eso?».
     */
    public String chatText(String systemPrompt, List<Message> messages) {
        return chat(systemPrompt, messages, false, CHAT_TEMPERATURE, properties.getChatMaxOutputTokens());
    }

    /** Un turno de la conversacion. {@code role} es "user" o "assistant". */
    public record Message(String role, String content) {
    }

    /** Una imagen ya codificada como data URL ({@code data:image/jpeg;base64,...}). */
    public record ImagePart(String dataUrl, String detail) {
    }

    /**
     * Una peticion con imagenes: el texto de la tarea y las imagenes en un solo
     * turno de usuario, con las partes {@code text} e {@code image_url} del formato
     * compatible con OpenAI. Temperatura casi nula: se transcribe, no se interpreta.
     */
    public String chatVision(String systemPrompt, String userText, List<ImagePart> images, int maxTokens) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AiUnavailableException(
                    "Falta la API key de DeepSeek (DEEPSEEK_API_KEY): sin ella no se pueden leer las imágenes del documento");
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("type", "text", "text", userText));
        for (ImagePart image : images) {
            parts.add(Map.of("type", "image_url",
                    "image_url", Map.of("url", image.dataUrl(), "detail", image.detail())));
        }
        List<Map<String, Object>> turns = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", parts));
        return send(turns, false, VISION_TEMPERATURE, maxTokens, "AI_VISION_MAX_OUTPUT_TOKENS");
    }

    private String chat(String systemPrompt, List<Message> messages, boolean json, double temperature, int maxTokens) {
        List<Map<String, Object>> turns = new ArrayList<>();
        turns.add(Map.of("role", "system", "content", systemPrompt));
        for (Message message : messages) {
            turns.add(Map.of("role", message.role(), "content", message.content()));
        }
        return send(turns, json, temperature, maxTokens, json ? "AI_MAX_OUTPUT_TOKENS" : "AI_CHAT_MAX_OUTPUT_TOKENS");
    }

    /** {@code maxTokensSetting} es la variable de entorno que sube ese tope, para decirla si se queda corto. */
    private String send(List<Map<String, Object>> turns, boolean json, double temperature, int maxTokens,
                        String maxTokensSetting) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of(
                "model", properties.getModel(),
                "stream", false,
                "temperature", temperature,
                "max_tokens", maxTokens,
                // Este modelo puede "pensar" antes de responder, y ese razonamiento sale
                // de la misma bolsa de max_tokens (verificado: unos cientos de palabras de
                // prompt ya le bastan para gastar mas de 2000 tokens pensando). El prompt
                // de sistema ya dice exactamente que hacer y en que formato, asi que no
                // hace falta que piense; sin esto, un prompt largo puede agotar el tope
                // razonando y devolver la respuesta vacia.
                "thinking", Map.of("type", "disabled"),
                "messages", turns));
        if (json) {
            body.put("response_format", Map.of("type", "json_object"));
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
            JsonNode choice = objectMapper.readTree(response).path("choices").path(0);
            // Cortada por el tope de max_tokens: el JSON llega a medias y no se podria
            // leer. Se dice tal cual, que es mucho mas util que "no se puede leer".
            if ("length".equals(choice.path("finish_reason").asText())) {
                log.warn("DeepSeek ha cortado la respuesta al llegar a max_tokens ({})", maxTokens);
                throw new AiUnavailableException(("La respuesta del modelo se ha cortado al llegar al tope de %,d "
                        + "tokens. Sube %s o prueba con un documento más corto.")
                        .formatted(maxTokens, maxTokensSetting));
            }
            return choice.path("message").path("content").asText();

        } catch (AiUnavailableException ex) {
            // Ya lleva un mensaje util: no hay que taparlo con el generico de abajo.
            throw ex;

        } catch (ResourceAccessException ex) {
            log.warn("No se ha podido contactar con DeepSeek en {}: {}", properties.getBaseUrl(), ex.getMessage());
            throw new AiUnavailableException(
                    "No se ha podido contactar con DeepSeek en %s. Comprueba la conexión a internet."
                            .formatted(properties.getBaseUrl()), ex);
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            log.warn("DeepSeek ha respondido con error {}: {}", status, ex.getResponseBodyAsString());
            throw new AiUnavailableException(mensajeParaError(status, errorDeDeepSeek(ex.getResponseBodyAsString())), ex);
        } catch (RestClientException ex) {
            log.warn("DeepSeek ha respondido con error: {}", ex.getMessage());
            throw new AiUnavailableException(
                    "DeepSeek ha respondido con un error. Revisa la API key y que el modelo '%s' exista."
                            .formatted(properties.getModel()), ex);
        } catch (Exception ex) {
            throw new AiUnavailableException("Respuesta de DeepSeek ilegible", ex);
        }
    }

    /**
     * El mensaje de "error.message" del cuerpo de DeepSeek (formato compatible con
     * OpenAI), o el cuerpo entero si no se puede leer como tal. Puede venir vacio:
     * para un 401, el HttpURLConnection de Java intercepta la respuesta para su
     * propia negociacion de autenticacion y a veces no deja leer el cuerpo del error.
     */
    private String errorDeDeepSeek(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "";
        try {
            String mensaje = objectMapper.readTree(responseBody).path("error").path("message").asText();
            return mensaje.isBlank() ? responseBody : mensaje;
        } catch (Exception ex) {
            return responseBody;
        }
    }

    /** Codigos documentados en api-docs.deepseek.com/quick_start/error_codes. */
    private String mensajeParaError(int status, String detalleDeepSeek) {
        String detalle = detalleDeepSeek.isBlank() ? "" : ": " + detalleDeepSeek;
        return switch (status) {
            case 401 -> "DeepSeek ha rechazado la API key. Revisa DEEPSEEK_API_KEY" + detalle;
            case 402 -> "La cuenta de DeepSeek se ha quedado sin saldo. Recarga en platform.deepseek.com" + detalle;
            case 404 -> "El modelo '%s' no existe en DeepSeek o no está disponible para esta cuenta%s"
                    .formatted(properties.getModel(), detalle);
            case 422 -> "DeepSeek ha rechazado los parámetros de la petición" + detalle;
            case 429 -> "DeepSeek está recibiendo demasiadas peticiones" + detalle
                    + ". Espera un momento y vuelve a generar.";
            case 500, 503 -> "El servidor de DeepSeek tiene un problema, no es un fallo de esta aplicación" + detalle;
            default -> "DeepSeek ha respondido con un error (%d)%s".formatted(status, detalle);
        };
    }
}
