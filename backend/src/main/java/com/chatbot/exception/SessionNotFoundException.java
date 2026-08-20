package com.chatbot.exception;

/**
 * Sessão sem histórico em memória. Mapeada para HTTP 404.
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String sessionId) {
        super("Nenhuma conversa encontrada para a sessão: " + sessionId);
    }
}
