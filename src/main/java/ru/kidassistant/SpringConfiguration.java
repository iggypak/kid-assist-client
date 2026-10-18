package ru.kidassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;
import java.time.Duration;

@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
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
    OllamaClient ollamaClient(AiProperties properties, HttpClient httpClient, ObjectMapper mapper) {
        return new OllamaClient(properties.ollama(), httpClient, mapper);
    }
}
