package com.chatbot.exception;

/**
 * Cota da API Gemini esgotada após as tentativas de retry.
 * Mapeada para HTTP 429 pelo {@link GlobalExceptionHandler}.
 */
public class RateLimitExceededException extends RuntimeException {

    public static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";

    public static final String USER_MESSAGE =
            "Cheguei no limite de requisições por minuto da API. Por favor, tente novamente em alguns segundos.";

    public RateLimitExceededException(Throwable cause) {
        super(USER_MESSAGE, cause);
    }
}
