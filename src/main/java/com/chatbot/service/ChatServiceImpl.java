package com.chatbot.service;

import com.chatbot.ai.GeminiAssistant;
import com.chatbot.dto.ChatRequest;
import com.chatbot.dto.ChatResponse;
import com.chatbot.exception.GeminiIntegrationException;
import com.chatbot.exception.SessionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Orquestra a chamada ao AI Service e traduz falhas externas
 * em exceções de domínio. Não conhece HTTP (isso é do Controller).
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final GeminiAssistant geminiAssistant;

    public ChatServiceImpl(GeminiAssistant geminiAssistant) {
        this.geminiAssistant = geminiAssistant;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId();
        log.info("Processando mensagem da sessão {}", sessionId);

        try {
            String reply = geminiAssistant.chat(sessionId, request.message());
            return new ChatResponse(sessionId, reply, Instant.now());
        } catch (RuntimeException ex) {
            throw new GeminiIntegrationException(
                    "Erro ao invocar o Gemini para a sessão " + sessionId,
                    ex
            );
        }
    }

    @Override
    public void clearSession(String sessionId) {
        boolean evicted = geminiAssistant.evictChatMemory(sessionId);
        if (!evicted) {
            throw new SessionNotFoundException(sessionId);
        }
        log.info("Memória da sessão {} removida", sessionId);
    }
}
