package ru.kidassistant.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

public record OllamaRequest(String model, boolean stream, boolean think,
                            List<Message> messages, Options options) {

    public record Message(String role, String content) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Options(int numPredict, int numCtx) {
    }
}
