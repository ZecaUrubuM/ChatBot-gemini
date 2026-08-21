package com.chatbot.service;

import com.chatbot.ai.GeminiAssistant;
import com.chatbot.dto.ChatRequest;
import com.chatbot.dto.ChatResponse;
import com.chatbot.exception.GeminiIntegrationException;
import com.chatbot.exception.GeminiRateLimitDetector;
import com.chatbot.exception.RateLimitExceededException;
import com.chatbot.exception.SessionNotFoundException;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Orquestra a chamada ao AI Service e traduz falhas externas
 * em exceções de domínio. Não conhece HTTP (isso é do Controller).
 *
 * Em 429 (cota Gemini), tenta de novo com espera exponencial
 * (2s, 4s, …) antes de falhar com {@link RateLimitExceededException}.
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    static final int DEFAULT_MAX_ATTEMPTS = 2;
    static final long DEFAULT_INITIAL_BACKOFF_MS = 2_000L;

    @FunctionalInterface
    interface MillisSleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final GeminiAssistant geminiAssistant;
    private final int maxAttempts;
    private final long initialBackoffMs;
    private final MillisSleeper sleeper;

    @Autowired
    public ChatServiceImpl(GeminiAssistant geminiAssistant) {
        this(geminiAssistant, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF_MS, Thread::sleep);
    }

    ChatServiceImpl(
            GeminiAssistant geminiAssistant,
            int maxAttempts,
            long initialBackoffMs,
            MillisSleeper sleeper
    ) {
        this.geminiAssistant = geminiAssistant;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMs = Math.max(0L, initialBackoffMs);
        this.sleeper = sleeper;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId();
        log.info("Processando mensagem da sessão {}", sessionId);

        RuntimeException lastRateLimit = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ChatMemory existingMemory = geminiAssistant.getChatMemory(sessionId);
            boolean hadMemory = existingMemory != null;
            List<ChatMessage> snapshot = hadMemory ? List.copyOf(existingMemory.messages()) : List.of();
            try {
                String response = geminiAssistant.chat(sessionId, request.message());
                return new ChatResponse(sessionId, response, Instant.now());
            } catch (RuntimeException ex) {
                restoreChatMemory(sessionId, hadMemory, snapshot);
                if (!GeminiRateLimitDetector.matches(ex)) {
                    throw new GeminiIntegrationException(
                            "Erro ao invocar o Gemini para a sessão " + sessionId,
                            ex
                    );
                }
                lastRateLimit = ex;
                if (attempt == maxAttempts) {
                    break;
                }
                long waitMs = initialBackoffMs * (1L << (attempt - 1));
                log.warn(
                        "Cota Gemini esgotada (429) na sessão {}. Nova tentativa {}/{} após {} ms",
                        sessionId,
                        attempt + 1,
                        maxAttempts,
                        waitMs
                );
                sleepBeforeRetry(waitMs);
            }
        }
        throw new RateLimitExceededException(lastRateLimit);
    }

    @Override
    public void clearSession(String sessionId) {
        boolean evicted = geminiAssistant.evictChatMemory(sessionId);
        if (!evicted) {
            throw new SessionNotFoundException(sessionId);
        }
        log.info("Memória da sessão {} removida", sessionId);
    }

    /**
     * O {@code DefaultAiServices} grava user / tool_call / tool_result na memória
     * antes de a chamada HTTP terminar. Um 429 no meio do loop deixaria o próximo
     * retry com user duplicado ou function call sem tool result — o Gemini recusa.
     */
    private void restoreChatMemory(String sessionId, boolean hadMemory, List<ChatMessage> snapshot) {
        if (!hadMemory) {
            geminiAssistant.evictChatMemory(sessionId);
            return;
        }
        ChatMemory memory = geminiAssistant.getChatMemory(sessionId);
        if (memory == null) {
            return;
        }
        if (snapshot.isEmpty()) {
            memory.clear();
            return;
        }
        memory.set(snapshot);
    }

    private void sleepBeforeRetry(long waitMs) {
        try {
            sleeper.sleep(waitMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RateLimitExceededException(interrupted);
        }
    }
}
