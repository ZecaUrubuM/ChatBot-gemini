package com.chatbot.dto;

import java.time.Instant;
import java.util.List;

/**
 * Envelope padronizado de erro da API.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp,
        String path,
        List<String> details
) {
}
