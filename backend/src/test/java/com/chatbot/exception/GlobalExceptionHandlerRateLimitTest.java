package com.chatbot.exception;

import com.chatbot.controller.ChatController;
import com.chatbot.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerRateLimitTest {

    private ChatService chatService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
    }

    @Test
    void rateLimitExceededDevolve429ComJsonEstruturado() throws Exception {
        when(chatService.chat(any()))
                .thenThrow(new RateLimitExceededException(new RateLimitException("HTTP 429")));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"cliente-1\",\"message\":\"oi\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(RateLimitExceededException.USER_MESSAGE));
    }

    @Test
    void rateLimitExceptionDoLangChain4jDevolve429() throws Exception {
        when(chatService.chat(any())).thenThrow(new RateLimitException("Too Many Requests"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"cliente-1\",\"message\":\"oi\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(RateLimitExceededException.USER_MESSAGE));
    }

    @Test
    void geminiIntegrationComCausa429TambemDevolve429() throws Exception {
        when(chatService.chat(any())).thenThrow(
                new GeminiIntegrationException("falha", new RateLimitException("quota"))
        );

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"cliente-1\",\"message\":\"oi\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }
}
