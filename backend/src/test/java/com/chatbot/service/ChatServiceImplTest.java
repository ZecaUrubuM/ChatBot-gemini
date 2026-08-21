package com.chatbot.service;

import com.chatbot.ai.GeminiAssistant;
import com.chatbot.dto.ChatRequest;
import com.chatbot.dto.ChatResponse;
import com.chatbot.exception.GeminiIntegrationException;
import com.chatbot.exception.RateLimitExceededException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceImplTest {

    private final GeminiAssistant assistant = mock(GeminiAssistant.class);
    private final ChatRequest request = new ChatRequest("sessao-1", "olá");

    @Test
    void retornaRespostaQuandoGeminiSucede() {
        when(assistant.chat("sessao-1", "olá")).thenReturn("Oi!");

        ChatResponse response = service(2, 2_000L).chat(request);

        assertEquals("sessao-1", response.sessionId());
        assertEquals("Oi!", response.response());
        verify(assistant, times(1)).chat("sessao-1", "olá");
    }

    @Test
    void retentaApos429EDevolveResposta() {
        when(assistant.chat("sessao-1", "olá"))
                .thenThrow(new RateLimitException("HTTP 429"))
                .thenReturn("Pronto, voltei.");

        List<Long> waits = new ArrayList<>();
        ChatResponse response = service(2, 2_000L, waits).chat(request);

        assertEquals("Pronto, voltei.", response.response());
        assertEquals(List.of(2_000L), waits);
        verify(assistant, times(2)).chat("sessao-1", "olá");
    }

    @Test
    void usaBackoffExponencialNasRetentativas() {
        when(assistant.chat("sessao-1", "olá"))
                .thenThrow(new HttpException(429, "Too Many Requests"))
                .thenThrow(new InvalidRequestException("status 429 RESOURCE_EXHAUSTED"))
                .thenThrow(new RateLimitException("ainda 429"));

        List<Long> waits = new ArrayList<>();
        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> service(3, 2_000L, waits).chat(request)
        );

        assertEquals(RateLimitExceededException.USER_MESSAGE, ex.getMessage());
        assertEquals(List.of(2_000L, 4_000L), waits);
        verify(assistant, times(3)).chat("sessao-1", "olá");
    }

    @Test
    void esgotaRetryELancaRateLimitExceeded() {
        when(assistant.chat("sessao-1", "olá"))
                .thenThrow(new RateLimitException("quota exceeded"));

        assertThrows(RateLimitExceededException.class, () -> service(2, 1L).chat(request));
        verify(assistant, times(2)).chat("sessao-1", "olá");
    }

    @Test
    void erroNao429ViraGeminiIntegrationExceptionSemRetry() {
        when(assistant.chat("sessao-1", "olá"))
                .thenThrow(new RuntimeException("modelo indisponível"));

        GeminiIntegrationException ex = assertThrows(
                GeminiIntegrationException.class,
                () -> service(3, 2_000L).chat(request)
        );

        assertInstanceOf(RuntimeException.class, ex.getCause());
        assertTrue(ex.getMessage().contains("sessao-1"));
        verify(assistant, times(1)).chat("sessao-1", "olá");
        verify(assistant).evictChatMemory("sessao-1");
    }

    @Test
    void retentativa429EvictaMemoriaDaSessaoNovaParaNaoDuplicarTurnos() {
        when(assistant.chat("sessao-1", "olá"))
                .thenThrow(new RateLimitException("HTTP 429"))
                .thenReturn("Pronto, voltei.");

        service(2, 1L).chat(request);

        verify(assistant).evictChatMemory("sessao-1");
        verify(assistant, times(2)).chat("sessao-1", "olá");
    }

    private ChatServiceImpl service(int maxAttempts, long initialBackoffMs) {
        return service(maxAttempts, initialBackoffMs, new ArrayList<>());
    }

    private ChatServiceImpl service(int maxAttempts, long initialBackoffMs, List<Long> waits) {
        return new ChatServiceImpl(assistant, maxAttempts, initialBackoffMs, waits::add);
    }
}
