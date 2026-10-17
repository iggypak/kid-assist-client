package ru.kidassistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {
    @TempDir Path tempDir;

    @Test
    void loadsAndNormalizesServerUrl() throws Exception {
        Path file = tempDir.resolve("config.properties");
        Files.writeString(file, "serverUrl=http://192.168.1.22:11434\nmodel=child-helper:latest\n");
        AppConfig config = AppConfig.load(file);
        assertEquals("http://192.168.1.22:11434/", config.serverUrl().toString());
        assertEquals("child-helper:latest", config.modelName());
    }

    @Test
    void rejectsInvalidServerPath() throws Exception {
        Path file = tempDir.resolve("config.properties");
        Files.writeString(file, "serverUrl=http://localhost:11434/api/chat\nmodel=child-helper:latest\n");
        assertThrows(java.io.IOException.class, () -> AppConfig.load(file));
    }
}
