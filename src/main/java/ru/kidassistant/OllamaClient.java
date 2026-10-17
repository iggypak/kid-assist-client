package ru.kidassistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** A stateless client: a new, single-user-message conversation for every request. */
public final class OllamaClient {
    private static final int MAX_OUTPUT_TOKENS = 400;
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI baseUrl;
    private final String model;

    public OllamaClient(AppConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), new ObjectMapper());
    }

    OllamaClient(AppConfig config, HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.baseUrl = config.serverUrl();
        this.model = config.modelName();
    }

    /** Checks HTTP availability only; it does not load the model or send a prompt. */
    public CompletableFuture<Boolean> isServerAvailable() {
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("api/tags"))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> response.statusCode() == 200);
    }

    public CompletableFuture<String> ask(String question) {
        final String body;
        try {
            body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "stream", false,
                    "messages", List.of(Map.of("role", "user", "content", question)),
                    "options", Map.of("num_predict", MAX_OUTPUT_TOKENS)
            ));
        } catch (JsonProcessingException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("api/chat"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> readAnswer(response.statusCode(), response.body()));
    }

    private String readAnswer(int statusCode, String responseBody) {
        try {
            JsonNode json = mapper.readTree(responseBody);
            if (statusCode != 200) {
                String reason = json.path("error").asText("Ошибка сервера");
                // Do not include uncontrolled, arbitrarily long server messages in the UI.
                throw new OllamaException("HTTP " + statusCode + ": " +
                        reason.substring(0, Math.min(reason.length(), 200)));
            }

            JsonNode content = json.path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new OllamaException("Модель вернула пустой ответ");
            }
            return content.asText().trim();
        } catch (JsonProcessingException exception) {
            throw new OllamaException("Некорректный JSON от Ollama", exception);
        }
    }

    public static final class OllamaException extends RuntimeException {
        public OllamaException(String message) {
            super(message);
        }

        public OllamaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
