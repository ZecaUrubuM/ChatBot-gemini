package com.chatbot.exception;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiRateLimitDetectorTest {

    @Test
    void detectaRateLimitException() {
        assertTrue(GeminiRateLimitDetector.matches(new RateLimitException("quota exceeded")));
    }

    @Test
    void detectaHttpException429() {
        assertTrue(GeminiRateLimitDetector.matches(new HttpException(429, "Too Many Requests")));
    }

    @Test
    void detectaInvalidRequestExceptionEncapsulando429() {
        HttpException http = new HttpException(429, "RESOURCE_EXHAUSTED");
        InvalidRequestException invalid = new InvalidRequestException("Request failed", http);
        assertTrue(GeminiRateLimitDetector.matches(invalid));
    }

    @Test
    void detectaRuntimeExceptionComCodigo429NaMensagem() {
        RuntimeException wrapped = new RuntimeException(
                "dev.langchain4j.exception.HttpException: HTTP 429 - You exceeded your current quota"
        );
        assertTrue(GeminiRateLimitDetector.matches(wrapped));
    }

    @Test
    void detectaCausaEncapsuladaEmGeminiIntegrationException() {
        RateLimitException rateLimit = new RateLimitException("429");
        GeminiIntegrationException wrapped = new GeminiIntegrationException("falha Gemini", rateLimit);
        assertTrue(GeminiRateLimitDetector.matches(wrapped));
    }

    @Test
    void ignoraErroQueNaoERateLimit() {
        assertFalse(GeminiRateLimitDetector.matches(new RuntimeException("timeout de rede")));
        assertFalse(GeminiRateLimitDetector.matches(new HttpException(500, "internal")));
        assertFalse(GeminiRateLimitDetector.matches(new InvalidRequestException("temperature not supported")));
    }
}
