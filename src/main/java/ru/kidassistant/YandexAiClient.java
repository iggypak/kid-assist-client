package ru.kidassistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.kidassistant.dto.YandexResponsesRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class YandexAiClient implements AiClient {
    private final AiProperties.Yandex config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Supplier<String> systemPrompt;
    private final URI baseUrl;

    public YandexAiClient(AiProperties.Yandex config, HttpClient httpClient, ObjectMapper mapper,
                          Supplier<String> systemPrompt) {
        if (config == null) {
            throw new IllegalArgumentException("Укажите настройки ai.yandex");
        }
        config.validate();
        this.config = config;
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.systemPrompt = systemPrompt;
        String address = config.baseUrl().toString();
        this.baseUrl = URI.create(address.endsWith("/") ? address : address + "/");
    }

    @Override
    public CompletableFuture<Boolean> isServerAvailable() {
        HttpRequest request = requestBuilder("models")
                .header("x-project", config.projectId())
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> response.statusCode() == 200 || response.statusCode() == 403);
    }

    @Override
    public CompletableFuture<String> ask(String question) {
        final String body;
        try {
            body = mapper.writeValueAsString(new YandexResponsesRequest(
                    config.model(), systemPrompt.get(), question, config.temperature(),
                    config.maxOutputTokens(), config.truncation(), config.store(), false));
        } catch (JsonProcessingException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        HttpRequest request = requestBuilder("responses")
                .timeout(config.requestTimeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> readAnswer(response.statusCode(), response.body()));
    }

    private HttpRequest.Builder requestBuilder(String resource) {
        return HttpRequest.newBuilder(baseUrl.resolve(resource))
                .header("Authorization", "Api-Key " + config.token())
                .header("OpenAI-Project", config.projectId());
    }

    private String readAnswer(int statusCode, String responseBody) {
        if (statusCode != 200) {
            String reason = switch (statusCode) {
                case 400 -> "Проверь модель и параметры запроса";
                case 401 -> "Неверный или недействительный API-ключ";
                case 403 -> "Нет доступа к облачной модели";
                case 404 -> "Облачный сервис или модель не найдены";
                case 429 -> "Превышен лимит запросов. Попробуй позже";
                default -> "Ошибка облачного сервиса";
            };
            throw new AiNotReachableException("Yandex AI: HTTP " + statusCode + ". " + reason);
        }

        try {
            JsonNode json = mapper.readTree(responseBody);
            if (json == null || !json.isObject()) {
                throw new AiNotReachableException("Некорректный ответ от Yandex AI");
            }
            String status = json.path("status").asText();
            if (status.equals("incomplete")) {
                throw new AiNotReachableException("Облачная модель вернула незавершённый ответ");
            }
            if (!status.equals("completed") || json.hasNonNull("error")) {
                throw new AiNotReachableException("Облачная модель не смогла завершить ответ");
            }

            StringJoiner answer = new StringJoiner("\n");
            for (JsonNode item : json.path("output")) {
                if (!item.path("type").asText().equals("message")
                        || !item.path("role").asText().equals("assistant")) {
                    continue;
                }
                for (JsonNode content : item.path("content")) {
                    JsonNode text = switch (content.path("type").asText()) {
                        case "output_text" -> content.path("text");
                        case "refusal" -> content.path("refusal");
                        default -> null;
                    };
                    if (text != null && text.isTextual() && !text.asText().isBlank()) {
                        answer.add(text.asText());
                    }
                }
            }
            String result = answer.toString().trim();
            if (result.isBlank()) {
                throw new AiNotReachableException("Облачная модель вернула пустой ответ");
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new AiNotReachableException("Некорректный JSON от Yandex AI", exception);
        }
    }
}
