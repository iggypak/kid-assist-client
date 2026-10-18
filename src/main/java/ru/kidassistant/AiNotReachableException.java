package ru.kidassistant;


public class AiNotReachableException extends RuntimeException {

    public AiNotReachableException(String message) {
        super(message);
    }

    public AiNotReachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
