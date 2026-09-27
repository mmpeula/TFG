package com.tfg.kanban.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El cliente se prueba contra un servidor HTTP de verdad, minimo y local, que
 * hace de doble de Groq: es lo que permite comprobar tambien lo que pasa cuando
 * no responde o devuelve basura, que es justo el caso que el usuario acaba viendo.
 */
class GroqClientTest {

    private HttpServer server;

    private AiProperties properties(String baseUrl) {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("clave-de-prueba");
        properties.setModel("openai/gpt-oss-120b");
        properties.setTimeout(Duration.ofSeconds(5));
        properties.setMaxOutputTokens(6000);
        return properties;
    }

    /** Levanta un doble de Groq que responde siempre lo mismo. */
    private GroqClient clientRespondiendo(int status, String body) throws IOException {
        return clientRespondiendo(status, body, new AtomicReference<>());
    }

    private GroqClient clientRespondiendo(int status, String body, AtomicReference<String> peticion)
            throws IOException {
        return clientRespondiendo(status, body, peticion, new AtomicReference<>());
    }

    private GroqClient clientRespondiendo(int status, String body, AtomicReference<String> peticion,
                                          AtomicReference<String> cabeceraAuth) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            peticion.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            cabeceraAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return new GroqClient(properties("http://127.0.0.1:" + server.getAddress().getPort()), new ObjectMapper());
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private static String contenidoRespuesta(String texto) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":%s}}]}"
                .formatted(new ObjectMapper().valueToTree(texto).toString());
    }

    @Test
    @DisplayName("Devuelve el contenido del mensaje, sin streaming, con la API key en la cabecera")
    void returnsMessageContent() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        GroqClient client = clientRespondiendo(200, contenidoRespuesta("{\"tasks\":[]}"), peticion, auth);

        assertThat(client.chatJson("eres un planificador", "reparte esto")).isEqualTo("{\"tasks\":[]}");

        assertThat(peticion.get())
                .contains("\"stream\":false")
                .contains("\"response_format\":{\"type\":\"json_object\"}")
                .contains("\"model\":\"openai/gpt-oss-120b\"")
                .contains("eres un planificador")
                .contains("reparte esto");
        assertThat(auth.get()).isEqualTo("Bearer clave-de-prueba");
    }

    @Test
    @DisplayName("Al generar JSON pide temperatura 0.2 y un tope de tokens de salida")
    void sendsTemperatureAndOutputTokenCap() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        GroqClient client = clientRespondiendo(200, contenidoRespuesta("{}"), peticion);

        client.chatJson("sistema", "usuario");

        var cuerpo = new ObjectMapper().readTree(peticion.get());
        assertThat(cuerpo.path("temperature").asDouble()).isEqualTo(0.2);
        // Sin esto, una respuesta sin token de fin generaria sin parar.
        assertThat(cuerpo.path("max_tokens").asInt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Con un prompt pequeno, max_tokens llega hasta el techo configurado")
    void maxTokensReachesTheConfiguredCeilingForASmallPrompt() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        GroqClient client = clientRespondiendo(200, contenidoRespuesta("{}"), peticion);

        client.chatJson("sistema breve", "usuario breve");

        assertThat(new ObjectMapper().readTree(peticion.get()).path("max_tokens").asInt()).isEqualTo(6000);
    }

    @Test
    @DisplayName("Con un prompt grande, max_tokens baja para no pasarse de la cuota de 8000 tokens/minuto")
    void maxTokensShrinksForALargePromptToStayUnderTheAccountQuota() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        GroqClient client = clientRespondiendo(200, contenidoRespuesta("{}"), peticion);

        // Unos 5000 caracteres de sistema mas otros tantos de usuario: por encima
        // de lo que cabria con el techo entero sin pasarse de la cuota.
        String promptGrande = "x".repeat(5000);
        client.chatJson(promptGrande, promptGrande);

        int maxTokens = new ObjectMapper().readTree(peticion.get()).path("max_tokens").asInt();
        assertThat(maxTokens).isLessThan(6000).isGreaterThanOrEqualTo(1000);
    }

    @Test
    @DisplayName("Aunque el prompt sea enorme, siempre se pide un minimo de tokens de salida")
    void maxTokensNeverGoesBelowTheFloor() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        GroqClient client = clientRespondiendo(200, contenidoRespuesta("{}"), peticion);

        client.chatJson("x".repeat(40_000), "usuario");

        assertThat(new ObjectMapper().readTree(peticion.get()).path("max_tokens").asInt()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Al generar JSON pide razonamiento bajo, para que no se coma el tope de tokens pensando")
    void sendsLowReasoningEffortForJson() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        GroqClient client = clientRespondiendo(200, contenidoRespuesta("{}"), peticion);

        client.chatJson("sistema", "usuario");

        assertThat(new ObjectMapper().readTree(peticion.get()).path("reasoning_effort").asText()).isEqualTo("low");
    }

    @Test
    @DisplayName("Una correccion viaja como conversacion: la respuesta anterior y los errores, en orden")
    void sendsTheWholeConversation() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        GroqClient client = clientRespondiendo(200, contenidoRespuesta("{}"), peticion);

        client.chatJson("sistema", java.util.List.of(
                new GroqClient.Message("user", "haz el plan"),
                new GroqClient.Message("assistant", "{\"tareas\":[]}"),
                new GroqClient.Message("user", "corrige esto")));

        var messages = new ObjectMapper().readTree(peticion.get()).path("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(2).path("role").asText()).isEqualTo("assistant");
        assertThat(messages.get(3).path("content").asText()).isEqualTo("corrige esto");
    }

    @Test
    @DisplayName("Si el modelo devuelve una respuesta vacia se avisa, no se devuelve null")
    void failsOnEmptyResponse() throws Exception {
        GroqClient client = clientRespondiendo(200, "");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("no ha devuelto ninguna respuesta");
    }

    @Test
    @DisplayName("Un 401/403 se traduce a que la API key no vale")
    void translatesAuthErrors() throws Exception {
        // 403 y no 401: para un 401, el HttpURLConnection de Java negocia su propia
        // autenticacion y a veces no deja leer el cuerpo de la respuesta de error.
        GroqClient client = clientRespondiendo(403, "{\"error\":{\"message\":\"Invalid API Key\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("API key")
                .hasMessageContaining("Invalid API Key");
    }

    @Test
    @DisplayName("Si un 401 llega sin cuerpo legible, el aviso sigue senalando la API key")
    void translatesAuthErrorsEvenWithoutABody() throws Exception {
        GroqClient client = clientRespondiendo(401, "{\"error\":{\"message\":\"Invalid API Key\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("API key");
    }

    @Test
    @DisplayName("Un 404 se traduce a que el modelo no existe, sin culpar a la API key")
    void translatesModelNotFound() throws Exception {
        GroqClient client = clientRespondiendo(404,
                "{\"error\":{\"message\":\"The model `openai/gpt-oss-999b` does not exist\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("modelo")
                .hasMessageContaining("does not exist")
                .hasMessageNotContaining("API key");
    }

    @Test
    @DisplayName("Un 429 por cuota de tokens se traduce a eso, no a un problema de la API key o el modelo")
    void translatesRateLimitErrors() throws Exception {
        GroqClient client = clientRespondiendo(429, "{\"error\":{\"message\":"
                + "\"Request too large for model on tokens per minute (TPM): Limit 8000, Requested 17099\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("cuota")
                .hasMessageContaining("Limit 8000, Requested 17099")
                .hasMessageNotContaining("API key");
    }

    @Test
    @DisplayName("Un 429 es un tipo de error especifico, que lleva cuanto dice Groq que hay que esperar")
    void rateLimitErrorsCarryTheRetryAfterGroqGives() throws Exception {
        GroqClient client = clientRespondiendo(429, "{\"error\":{\"message\":"
                + "\"Rate limit reached... Please try again in 16.4925s. Upgrade to Dev Tier\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(GroqClient.RateLimitedException.class)
                .extracting(ex -> ((GroqClient.RateLimitedException) ex).getRetryAfterSeconds())
                .isEqualTo(16.4925);
    }

    @Test
    @DisplayName("Si Groq no dice cuanto esperar en un 429, se usa una espera por defecto")
    void rateLimitErrorsDefaultTheRetryAfterWhenGroqDoesNotSayHowLong() throws Exception {
        GroqClient client = clientRespondiendo(429, "{\"error\":{\"message\":\"Rate limit reached\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(GroqClient.RateLimitedException.class)
                .extracting(ex -> ((GroqClient.RateLimitedException) ex).getRetryAfterSeconds())
                .isEqualTo(5.0);
    }

    @Test
    @DisplayName("Por mucho que Groq pida esperar, nunca se espera mas de 30 segundos")
    void rateLimitRetryAfterIsCappedAt30Seconds() throws Exception {
        GroqClient client = clientRespondiendo(429,
                "{\"error\":{\"message\":\"Rate limit reached. Please try again in 120s\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(GroqClient.RateLimitedException.class)
                .extracting(ex -> ((GroqClient.RateLimitedException) ex).getRetryAfterSeconds())
                .isEqualTo(30.0);
    }

    @Test
    @DisplayName("Un 413 (peticion en si demasiado grande) no se trata como el 429: esperar no lo arregla")
    void payloadTooLargeIsNotRetriedLikeRateLimit() throws Exception {
        GroqClient client = clientRespondiendo(413, "{\"error\":{\"message\":"
                + "\"Request too large for model on tokens per minute (TPM): Limit 8000, Requested 17099\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .isNotInstanceOf(GroqClient.RateLimitedException.class)
                .hasMessageContaining("cuota");
    }

    @Test
    @DisplayName("Una respuesta ilegible no se propaga como error interno")
    void translatesUnreadableResponses() throws Exception {
        GroqClient client = clientRespondiendo(200, "esto no es json");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("ilegible");
    }

    @Test
    @DisplayName("Si no se puede contactar con Groq, el mensaje dice exactamente eso")
    void explainsWhenGroqIsUnreachable() {
        // Puerto cerrado a proposito: es el caso mas comun para quien prueba la app sin red.
        GroqClient client = new GroqClient(properties("http://127.0.0.1:1"), new ObjectMapper());

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("Groq");
    }
}
