package ru.kidassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
@EnableScheduling
public class SpringConfiguration {

    @Bean(destroyMethod = "shutdownNow")
    HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    TrayService trayService() {
        return new TrayService(Platform::runLater);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "yandex", matchIfMissing = true)
    SystemPromptCache systemPromptCache(AiProperties properties) throws IOException {
        AiProperties.Yandex config = properties.yandex();
        if (config == null) {
            throw new IllegalArgumentException("Укажите настройки ai.yandex");
        }
        config.validate();
        return new SystemPromptCache(config.systemPromptPath());
    }

    @Bean
    AiClient aiClient(AiProperties properties, HttpClient httpClient, ObjectMapper mapper,
                      ObjectProvider<SystemPromptCache> systemPromptCache) {
        return switch (properties.provider()) {
            case OLLAMA -> new OllamaClient(properties.ollama(), httpClient, mapper);
            case YANDEX -> new YandexAiClient(properties.yandex(), httpClient, mapper, systemPromptCache.getObject());
        };
    }
}
