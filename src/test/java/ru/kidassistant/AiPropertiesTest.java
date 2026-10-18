package ru.kidassistant;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class AiPropertiesTest {

    @Test
    void normalizesBaseUrl() {
        AiProperties.Ollama config = new AiProperties.Ollama(
                URI.create("http://192.168.1.22:11434"), "child-helper:latest");
        assertEquals("http://192.168.1.22:11434/", config.baseUrl().toString());
        assertEquals("child-helper:latest", config.model());
    }

    @Test
    void rejectsInvalidServerPath() {
        assertThrows(IllegalArgumentException.class, () -> new AiProperties.Ollama(
                URI.create("http://localhost:11434/api/chat"), "child-helper:latest"));
    }
}
