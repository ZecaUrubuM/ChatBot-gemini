package com.chatbot.dto;

import java.time.Instant;

/**
 * Resposta de uma interação com o chatbot.
 */
public record ChatResponse(
        String sessionId,
        String reply,
        Instant timestamp
) {
}
