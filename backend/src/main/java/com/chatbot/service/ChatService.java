package com.chatbot.service;

import com.chatbot.dto.ChatRequest;
import com.chatbot.dto.ChatResponse;

/**
 * Caso de uso de conversação. Isola o Controller da API do LangChain4j
 * (Dependency Inversion): o Controller depende desta abstração, não do Gemini.
 */
public interface ChatService {

    ChatResponse chat(ChatRequest request);

    void clearSession(String sessionId);
}
