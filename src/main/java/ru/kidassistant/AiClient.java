package ru.kidassistant;


import java.util.concurrent.CompletableFuture;


public interface AiClient {
    CompletableFuture<Boolean> isServerAvailable();
    CompletableFuture<String> ask(String question);
}
