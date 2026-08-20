package com.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo da requisição de conversa.
 *
 * {@code sessionId} é a chave da memória: o mesmo valor em chamadas
 * sucessivas reutiliza o histórico. Pode ser um userId, um UUID de sessão
 * ou qualquer identificador estável do cliente.
 */
public record ChatRequest(

        @NotBlank(message = "sessionId é obrigatório")
        @Size(max = 100, message = "sessionId deve ter no máximo 100 caracteres")
        String sessionId,

        @NotBlank(message = "message é obrigatória")
        @Size(max = 8000, message = "message deve ter no máximo 8000 caracteres")
        String message
) {
}
