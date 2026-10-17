package ru.kidassistant;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Local client configuration. No secrets or model instructions are stored here. */
public record AppConfig(URI serverUrl, String modelName) {

    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".config", "kid-assistant", "config.properties");
    }

    public static AppConfig load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Не найден файл настроек: " + path);
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }

        String url = properties.getProperty("serverUrl", "").trim();
        String model = properties.getProperty("model", "").trim();
        if (url.isBlank() || model.isBlank()) {
            throw new IOException("В файле настроек укажите serverUrl и model: " + path);
        }

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isBlank() && !uri.getPath().equals("/"))) {
                throw new IOException("serverUrl должен быть адресом Ollama, например http://192.168.1.10:11434");
            }
            // Trailing slash is important for URI.resolve("api/chat").
            URI normalized = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), "/", null, null);
            return new AppConfig(normalized, model);
        } catch (URISyntaxException exception) {
            throw new IOException("Некорректный serverUrl: " + exception.getMessage(), exception);
        }
    }
}
