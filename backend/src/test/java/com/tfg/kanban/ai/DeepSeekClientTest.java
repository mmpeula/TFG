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
 * hace de doble de DeepSeek: es lo que permite comprobar tambien lo que pasa
 * cuando no responde o devuelve basura, que es justo el caso que el usuario
 * acaba viendo.
 */
class DeepSeekClientTest {

    private HttpServer server;

    private AiProperties properties(String baseUrl) {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("clave-de-prueba");
        properties.setModel("deepseek-flash");
        properties.setTimeout(Duration.ofSeconds(5));
        properties.setMaxOutputTokens(6000);
        return properties;
    }

    /** Levanta un doble de DeepSeek que responde siempre lo mismo. */
    private DeepSeekClient clientRespondiendo(int status, String body) throws IOException {
        return clientRespondiendo(status, body, new AtomicReference<>());
    }

    private DeepSeekClient clientRespondiendo(int status, String body, AtomicReference<String> peticion)
            throws IOException {
        return clientRespondiendo(status, body, peticion, new AtomicReference<>());
    }

    private DeepSeekClient clientRespondiendo(int status, String body, AtomicReference<String> peticion,
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
        return new DeepSeekClient(properties("http://127.0.0.1:" + server.getAddress().getPort()), new ObjectMapper());
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
        DeepSeekClient client = clientRespondiendo(200, contenidoRespuesta("{\"tasks\":[]}"), peticion, auth);

        assertThat(client.chatJson("eres un planificador", "reparte esto")).isEqualTo("{\"tasks\":[]}");

        assertThat(peticion.get())
                .contains("\"stream\":false")
                .contains("\"response_format\":{\"type\":\"json_object\"}")
                .contains("\"model\":\"deepseek-flash\"")
                .contains("eres un planificador")
                .contains("reparte esto");
        assertThat(auth.get()).isEqualTo("Bearer clave-de-prueba");
    }

    @Test
    @DisplayName("Al generar JSON pide temperatura baja y un tope de tokens de salida")
    void sendsTemperatureAndOutputTokenCap() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        DeepSeekClient client = clientRespondiendo(200, contenidoRespuesta("{}"), peticion);

        client.chatJson("sistema", "usuario");

        var cuerpo = new ObjectMapper().readTree(peticion.get());
        assertThat(cuerpo.path("temperature").asDouble()).isEqualTo(0.2);
        // Sin esto, una respuesta sin token de fin generaria sin parar.
        assertThat(cuerpo.path("max_tokens").asInt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Pide siempre el razonamiento desactivado, para no gastar el tope de tokens pensando")
    void disablesThinking() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        DeepSeekClient client = clientRespondiendo(200, contenidoRespuesta("{}"), peticion);

        client.chatJson("sistema", "usuario");

        assertThat(new ObjectMapper().readTree(peticion.get()).path("thinking").path("type").asText())
                .isEqualTo("disabled");
    }

    @Test
    @DisplayName("max_tokens es siempre el techo configurado, sin importar el tamano del prompt")
    void maxTokensAlwaysMatchesTheConfiguredCeiling() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        DeepSeekClient client = clientRespondiendo(200, contenidoRespuesta("{}"), peticion);

        client.chatJson("x".repeat(40_000), "usuario");

        assertThat(new ObjectMapper().readTree(peticion.get()).path("max_tokens").asInt()).isEqualTo(6000);
    }

    @Test
    @DisplayName("El asistente manda la conversacion entera, en orden y sin forzar JSON")
    void chatTextSendsTheWholeConversation() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        DeepSeekClient client = clientRespondiendo(200, contenidoRespuesta("Empieza por el modelo E/R."), peticion);

        String respuesta = client.chatText("sistema", java.util.List.of(
                new DeepSeekClient.Message("user", "que hago primero?"),
                new DeepSeekClient.Message("assistant", "El modelo de datos."),
                new DeepSeekClient.Message("user", "y despues?")));

        assertThat(respuesta).isEqualTo("Empieza por el modelo E/R.");
        var cuerpo = new ObjectMapper().readTree(peticion.get());
        var messages = cuerpo.path("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(2).path("role").asText()).isEqualTo("assistant");
        assertThat(messages.get(3).path("content").asText()).isEqualTo("y despues?");
        assertThat(cuerpo.has("response_format")).isFalse();
    }

    @Test
    @DisplayName("Si la respuesta se corta por el tope de tokens se dice asi, no como una respuesta ilegible")
    void explainsWhenTheAnswerIsCutByTheTokenLimit() throws Exception {
        DeepSeekClient client = clientRespondiendo(200,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"tareas\\\":[{\"},"
                        + "\"finish_reason\":\"length\"}]}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("se ha cortado")
                .hasMessageContaining("AI_MAX_OUTPUT_TOKENS");
    }

    @Test
    @DisplayName("Si el modelo devuelve una respuesta vacia se avisa, no se devuelve null")
    void failsOnEmptyResponse() throws Exception {
        DeepSeekClient client = clientRespondiendo(200, "");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("no ha devuelto ninguna respuesta");
    }

    @Test
    @DisplayName("Un 401 se traduce a que la API key no vale")
    void translatesAuthErrors() throws Exception {
        // Solo se comprueba el mensaje fijo, no el cuerpo de DeepSeek: para un 401,
        // el HttpURLConnection de Java negocia su propia autenticacion y a veces no
        // deja leer el cuerpo de la respuesta de error.
        DeepSeekClient client = clientRespondiendo(401,
                "{\"error\":{\"message\":\"Authentication Fails, Your api key is invalid\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("API key");
    }

    @Test
    @DisplayName("Un 402 se traduce a que la cuenta se ha quedado sin saldo")
    void translatesInsufficientBalance() throws Exception {
        DeepSeekClient client = clientRespondiendo(402,
                "{\"error\":{\"message\":\"Insufficient Balance\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("saldo")
                .hasMessageNotContaining("API key");
    }

    @Test
    @DisplayName("Un 404 se traduce a que el modelo no existe, sin culpar a la API key")
    void translatesModelNotFound() throws Exception {
        DeepSeekClient client = clientRespondiendo(404,
                "{\"error\":{\"message\":\"Model Not Exist\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("modelo")
                .hasMessageContaining("Model Not Exist")
                .hasMessageNotContaining("API key");
    }

    @Test
    @DisplayName("Un 422 se traduce a que los parametros de la peticion no valen")
    void translatesInvalidParameters() throws Exception {
        DeepSeekClient client = clientRespondiendo(422,
                "{\"error\":{\"message\":\"Your request contains invalid parameters\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("parametros");
    }

    @Test
    @DisplayName("Un 429 se traduce a que hay demasiadas peticiones, sin culpar a la API key")
    void translatesRateLimitErrors() throws Exception {
        DeepSeekClient client = clientRespondiendo(429,
                "{\"error\":{\"message\":\"You are sending requests too quickly\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("demasiadas peticiones")
                .hasMessageNotContaining("API key");
    }

    @Test
    @DisplayName("Un 500/503 se traduce a un problema del servidor de DeepSeek, no de esta aplicacion")
    void translatesServerErrors() throws Exception {
        DeepSeekClient client = clientRespondiendo(503, "{\"error\":{\"message\":\"Server overloaded\"}}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("servidor de DeepSeek");
    }

    @Test
    @DisplayName("Una respuesta ilegible no se propaga como error interno")
    void translatesUnreadableResponses() throws Exception {
        DeepSeekClient client = clientRespondiendo(200, "esto no es json");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("ilegible");
    }

    @Test
    @DisplayName("Si no se puede contactar con DeepSeek, el mensaje dice exactamente eso")
    void explainsWhenDeepSeekIsUnreachable() {
        // Puerto cerrado a proposito: es el caso mas comun para quien prueba la app sin red.
        DeepSeekClient client = new DeepSeekClient(properties("http://127.0.0.1:1"), new ObjectMapper());

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("DeepSeek");
    }
}
