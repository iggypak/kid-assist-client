package ru.kidassistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class SystemPromptCache implements Supplier<String> {
    private static final Logger log = LoggerFactory.getLogger(SystemPromptCache.class);

    private final Path path;
    private volatile String content;

    public SystemPromptCache(Path path) throws IOException {
        if (path == null || path.toString().isBlank()) {
            throw new IllegalArgumentException("Укажите ai.yandex.system-prompt-path");
        }
        this.path = path.toAbsolutePath().normalize();
        content = readPrompt();
    }

    @Override
    public String get() {
        return content;
    }

    @Scheduled(initialDelay = 2, fixedRate = 2, timeUnit = TimeUnit.MINUTES)
    public void reload() {
        try {
            content = readPrompt();
        } catch (IOException | SecurityException exception) {
            log.warn("Не удалось обновить системный промпт из {} ({}). Сохранён предыдущий текст",
                    path, exception.getClass().getSimpleName());
        }
    }

    private String readPrompt() throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw new IOException("Файл системного промпта пуст: " + path);
        }
        return text;
    }
}
