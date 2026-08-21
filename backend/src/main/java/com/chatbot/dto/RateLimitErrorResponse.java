package com.chatbot.dto;

/**
 * Corpo HTTP 429 quando a cota da API Gemini está esgotada.
 */
public record RateLimitErrorResponse(
        String error,
        String message
) {
}
