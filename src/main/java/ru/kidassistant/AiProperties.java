package ru.kidassistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(Ollama ollama, Cloud cloud) {

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

    public record Cloud(URI baseUrl, String token) {
        @Override
        public String toString() {
            return "Cloud[baseUrl=" + baseUrl + ", token=***]";
        }
    }
}
