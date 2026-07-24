package net.ddns.adambravo79.tmill.exception;

public class GroqRateLimitException extends RuntimeException {
    public GroqRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }

    public GroqRateLimitException(String message) {
        super(message);
    }
}
