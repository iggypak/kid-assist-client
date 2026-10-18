package ru.kidassistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record YandexResponsesRequest(String model, String instructions, String input,
                                     Double temperature, int maxOutputTokens,
                                     String truncation, boolean store, boolean stream) {
}
