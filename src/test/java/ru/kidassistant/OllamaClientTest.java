package ru.kidassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OllamaClientTest {
    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> received = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] body = "{\"models\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/chat", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"message\":{\"role\":\"assistant\",\"content\":\"Открой меню приложений.\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsExactlyOneQuestionWithoutContextHistory() throws Exception {
        URI address = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        OllamaClient client = new OllamaClient(new AppConfig(address, "child-helper:latest"));
        assertTrue(client.isServerAvailable().get(5, TimeUnit.SECONDS));
        assertEquals("Открой меню приложений.", client.ask("Как открыть Firefox?").get(5, TimeUnit.SECONDS));

        JsonNode request = mapper.readTree(received.get());
        assertEquals("child-helper:latest", request.path("model").asText());
        assertFalse(request.path("stream").asBoolean());
        assertEquals(1, request.path("messages").size());
        assertEquals("user", request.path("messages").get(0).path("role").asText());
        assertEquals("Как открыть Firefox?", request.path("messages").get(0).path("content").asText());
    }
}
