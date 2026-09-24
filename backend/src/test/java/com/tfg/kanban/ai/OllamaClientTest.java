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
 * El cliente se prueba contra un servidor HTTP de verdad, minimo y local: es lo
 * que permite comprobar tambien lo que pasa cuando Ollama no responde o devuelve
 * basura, que es justo el caso que el usuario acaba viendo.
 */
class OllamaClientTest {

    private HttpServer server;

    private AiProperties properties(String baseUrl) {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl(baseUrl);
        properties.setModel("llama3");
        properties.setTimeout(Duration.ofSeconds(5));
        return properties;
    }

    /** Levanta un Ollama de mentira que responde siempre lo mismo. */
    private OllamaClient clientRespondiendo(int status, String body) throws IOException {
        return clientRespondiendo(status, body, new AtomicReference<>());
    }

    private OllamaClient clientRespondiendo(int status, String body, AtomicReference<String> peticion)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            peticion.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return new OllamaClient(properties("http://127.0.0.1:" + server.getAddress().getPort()), new ObjectMapper());
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    @DisplayName("Devuelve el contenido del mensaje y pide la respuesta en JSON, sin streaming")
    void returnsMessageContent() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        OllamaClient client = clientRespondiendo(200,
                "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"tasks\\\":[]}\"}}", peticion);

        assertThat(client.chatJson("eres un planificador", "reparte esto")).isEqualTo("{\"tasks\":[]}");

        assertThat(peticion.get())
                .contains("\"stream\":false")
                .contains("\"format\":\"json\"")
                .contains("\"model\":\"llama3\"")
                .contains("eres un planificador")
                .contains("reparte esto");
    }

    @Test
    @DisplayName("Al generar JSON pide temperatura 0.3 y una ventana de contexto de al menos 8192")
    void sendsTemperatureAndContextWindow() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        OllamaClient client = clientRespondiendo(200,
                "{\"message\":{\"role\":\"assistant\",\"content\":\"{}\"}}", peticion);

        client.chatJson("sistema", "usuario");

        var options = new ObjectMapper().readTree(peticion.get()).path("options");
        assertThat(options.path("temperature").asDouble()).isEqualTo(0.3);
        assertThat(options.path("num_ctx").asInt()).isGreaterThanOrEqualTo(8192);
    }

    @Test
    @DisplayName("Una correccion viaja como conversacion: la respuesta anterior y los errores, en orden")
    void sendsTheWholeConversation() throws Exception {
        AtomicReference<String> peticion = new AtomicReference<>();
        OllamaClient client = clientRespondiendo(200,
                "{\"message\":{\"role\":\"assistant\",\"content\":\"{}\"}}", peticion);

        client.chatJson("sistema", java.util.List.of(
                new OllamaClient.Message("user", "haz el plan"),
                new OllamaClient.Message("assistant", "{\"tareas\":[]}"),
                new OllamaClient.Message("user", "corrige esto")));

        var messages = new ObjectMapper().readTree(peticion.get()).path("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(2).path("role").asText()).isEqualTo("assistant");
        assertThat(messages.get(3).path("content").asText()).isEqualTo("corrige esto");
    }

    @Test
    @DisplayName("Si el modelo devuelve una respuesta vacia se avisa, no se devuelve null")
    void failsOnEmptyResponse() throws Exception {
        OllamaClient client = clientRespondiendo(200, "");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("no ha devuelto ninguna respuesta");
    }

    @Test
    @DisplayName("Un error del servidor se traduce a «revisa que el modelo este descargado»")
    void translatesServerErrors() throws Exception {
        OllamaClient client = clientRespondiendo(404, "{\"error\":\"model 'llama3' not found\"}");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("llama3");
    }

    @Test
    @DisplayName("Una respuesta ilegible no se propaga como error interno")
    void translatesUnreadableResponses() throws Exception {
        OllamaClient client = clientRespondiendo(200, "esto no es json");

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("ilegible");
    }

    @Test
    @DisplayName("Si Ollama no esta arrancado, el mensaje dice exactamente eso")
    void explainsWhenOllamaIsDown() {
        // Puerto cerrado a proposito: es el caso mas comun para quien prueba la app.
        OllamaClient client = new OllamaClient(properties("http://127.0.0.1:1"), new ObjectMapper());

        assertThatThrownBy(() -> client.chatJson("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("Ollama esta arrancado");
    }

    @Test
    @DisplayName("El modelo se reconoce tanto con su nombre exacto como con etiqueta")
    void detectsInstalledModel() throws Exception {
        assertThat(clientRespondiendo(200, "{\"models\":[{\"name\":\"llama3\"}]}").isModelAvailable()).isTrue();
        server.stop(0);

        assertThat(clientRespondiendo(200, "{\"models\":[{\"name\":\"llama3:latest\"}]}").isModelAvailable()).isTrue();
    }

    @Test
    @DisplayName("Si el modelo configurado no esta descargado, se responde que no")
    void detectsMissingModel() throws Exception {
        OllamaClient client = clientRespondiendo(200, "{\"models\":[{\"name\":\"mistral:latest\"}]}");

        assertThat(client.isModelAvailable()).isFalse();
    }

    @Test
    @DisplayName("Sin servidor, la comprobacion devuelve false en vez de estallar")
    void modelCheckSurvivesOutage() {
        OllamaClient client = new OllamaClient(properties("http://127.0.0.1:1"), new ObjectMapper());

        assertThat(client.isModelAvailable()).isFalse();
    }
}
