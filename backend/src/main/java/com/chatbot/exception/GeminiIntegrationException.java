package com.chatbot.exception;

/**
 * Falha ao chamar a API do Gemini (rede, autenticação, modelo, etc.).
 * Mapeada para HTTP 502 pelo {@link GlobalExceptionHandler}.
 * Cota/429 vira {@link RateLimitExceededException}, não esta classe.
 */
public class GeminiIntegrationException extends RuntimeException {

    public GeminiIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
