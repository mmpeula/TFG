package com.tfg.kanban.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente del servidor local de Ollama.
 *
 * Se usa el endpoint /api/chat con {@code format: "json"} y {@code stream: false}:
 * interesa una respuesta unica y estructurada, no un flujo de texto. El modelo
 * corre en la maquina del usuario, asi que ningun dato del proyecto sale de ella.
 */
@Slf4j
@Component
public class OllamaClient {

    /** En el chat interesa algo mas de soltura que al generar JSON, pero sin inventar. */
    private static final double CHAT_TEMPERATURE = 0.4;

    /** Lo que se espera a que el servidor local coja el telefono, no a que conteste. */
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OllamaClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        // Conectar con un servidor local es inmediato o no va a pasar; lo que tarda
        // es la respuesta, porque un modelo de 8B en CPU se lo toma con calma. De
        // ahi los dos tiempos de espera tan distintos.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        factory.setReadTimeout((int) Math.min(Integer.MAX_VALUE, properties.getTimeout().toMillis()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /** Devuelve el contenido del mensaje del modelo, que se espera que sea JSON. */
    public String chatJson(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, List.of(new Message("user", userPrompt)), true, properties.getTemperature());
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

    private String chat(String systemPrompt, List<Message> messages, boolean json, double temperature) {
        List<Map<String, Object>> turns = new ArrayList<>();
        turns.add(Map.of("role", "system", "content", systemPrompt));
        for (Message message : messages) {
            turns.add(Map.of("role", message.role(), "content", message.content()));
        }

        Map<String, Object> body = new LinkedHashMap<>(Map.of(
                "model", properties.getModel(),
                "stream", false,
                "options", Map.of("temperature", temperature),
                "messages", turns));
        if (json) {
            body.put("format", "json");
        }

        try {
            String response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new AiUnavailableException("El modelo no ha devuelto ninguna respuesta");
            }
            return objectMapper.readTree(response).path("message").path("content").asText();

        } catch (AiUnavailableException ex) {
            // Ya lleva un mensaje util: no hay que taparlo con el generico de abajo.
            throw ex;

        } catch (ResourceAccessException ex) {
            log.warn("No se ha podido contactar con Ollama en {}: {}", properties.getBaseUrl(), ex.getMessage());
            throw new AiUnavailableException(
                    "No se ha podido contactar con el modelo local en %s. Comprueba que Ollama esta arrancado."
                            .formatted(properties.getBaseUrl()), ex);
        } catch (RestClientException ex) {
            log.warn("Ollama ha respondido con error: {}", ex.getMessage());
            throw new AiUnavailableException(
                    "El modelo local ha respondido con un error. Revisa que el modelo '%s' este descargado."
                            .formatted(properties.getModel()), ex);
        } catch (Exception ex) {
            throw new AiUnavailableException("Respuesta del modelo local ilegible", ex);
        }
    }

    /** Comprueba que el servidor responde y que el modelo configurado esta descargado. */
    public boolean isModelAvailable() {
        try {
            String tags = restClient.get().uri("/api/tags").retrieve().body(String.class);
            if (tags == null) return false;
            String wanted = properties.getModel();
            var models = objectMapper.readTree(tags).path("models");
            for (var model : models) {
                String name = model.path("name").asText();
                if (name.equals(wanted) || name.startsWith(wanted + ":")) return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }
}
