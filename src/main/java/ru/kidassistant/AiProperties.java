package ru.kidassistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(@DefaultValue("YANDEX") Provider provider, Ollama ollama, Yandex yandex) {

    public enum Provider {
        OLLAMA, YANDEX
    }

    public record Ollama(URI baseUrl, String model) {
        public Ollama {
            if (baseUrl == null || model == null || model.isBlank()) {
                throw new IllegalArgumentException("Укажите ai.ollama.base-url и ai.ollama.model");
            }
            String scheme = baseUrl.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                    || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                    || (baseUrl.getPath() != null && !baseUrl.getPath().isBlank()
                    && !baseUrl.getPath().equals("/"))) {
                throw new IllegalArgumentException("ai.ollama.base-url должен быть адресом Ollama, например http://192.168.1.10:11434");
            }
            try {
                baseUrl = new URI(scheme, null, baseUrl.getHost(), baseUrl.getPort(), "/", null, null);
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("Некорректный адрес Ollama", exception);
            }
            model = model.trim();
        }
    }

    public record Yandex(URI baseUrl, String token, String projectId, String model,
                         String instructions, Double temperature, int maxOutputTokens,
                         String truncation, boolean store, @DefaultValue("5m") Duration requestTimeout) {

        public void validate() {
            if (baseUrl == null || baseUrl.getHost() == null
                    || (!"http".equalsIgnoreCase(baseUrl.getScheme())
                    && !"https".equalsIgnoreCase(baseUrl.getScheme()))
                    || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null
                    || baseUrl.getFragment() != null || baseUrl.getPort() > 65535) {
                throw new IllegalArgumentException("Укажите корректный ai.yandex.base-url, например https://ai.api.cloud.yandex.net/v1/");
            }
            if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n")) {
                throw new IllegalArgumentException("Укажите ai.yandex.token через YANDEX_API_KEY");
            }
            if (projectId == null || projectId.isBlank() || projectId.contains("\r") || projectId.contains("\n")) {
                throw new IllegalArgumentException("Укажите ai.yandex.project-id");
            }
            if (model == null || model.isBlank() || instructions == null || instructions.isBlank()) {
                throw new IllegalArgumentException("Укажите ai.yandex.model и ai.yandex.instructions");
            }
            if (temperature != null && (!Double.isFinite(temperature) || temperature < 0 || temperature > 2)) {
                throw new IllegalArgumentException("ai.yandex.temperature должен быть от 0 до 2");
            }
            if (maxOutputTokens <= 0 || requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
                throw new IllegalArgumentException("ai.yandex.max-output-tokens и ai.yandex.request-timeout должны быть положительными");
            }
            if (truncation != null && !truncation.equals("disabled") && !truncation.equals("auto")) {
                throw new IllegalArgumentException("ai.yandex.truncation должен быть disabled или auto");
            }
        }

        @Override
        public String toString() {
            return "Yandex[baseUrl=" + baseUrl + ", token=***, projectId=" + projectId + ", model=" + model + "]";
        }
    }
}
