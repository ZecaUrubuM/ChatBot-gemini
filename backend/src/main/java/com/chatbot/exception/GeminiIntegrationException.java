package com.chatbot.exception;

/**
 * Falha ao chamar a API do Gemini (rede, autenticação, quota, modelo, etc.).
 * Mapeada para HTTP 502 pelo {@link GlobalExceptionHandler}.
 */
public class GeminiIntegrationException extends RuntimeException {

    public GeminiIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
