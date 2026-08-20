package com.chatbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Instancia o {@link OpenAiChatModel} no endpoint OpenAI-compatible da Google
 * ({@code https://generativelanguage.googleapis.com/v1beta/openai/}) com
 * {@code gemini-3.6-flash}.
 *
 * Não envia temperature / topP / maxTokens / frequencyPenalty / presencePenalty /
 * reasoningEffort / thinking_budget: o 3.6-flash devolve 400 INVALID_ARGUMENT.
 * Thinking no Gemini 3 não desliga; function calling exige {@code thought_signature}.
 * O {@link ThoughtSignatureHttpClientBuilder} faz o round-trip (ou o sentinel oficial).
 */
@Configuration
public class OpenAiGeminiChatModelConfig {

    @Bean
    public ChatModel chatModel(
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName,
            @Value("${langchain4j.open-ai.chat-model.timeout}") Duration timeout,
            @Value("${langchain4j.open-ai.chat-model.max-retries}") int maxRetries,
            @Value("${langchain4j.open-ai.chat-model.log-requests:true}") boolean logRequests,
            @Value("${langchain4j.open-ai.chat-model.log-responses:true}") boolean logResponses
    ) {
        GeminiThoughtSignatureSupport signatures = new GeminiThoughtSignatureSupport(objectMapper);

        return OpenAiChatModel.builder()
                .httpClientBuilder(new ThoughtSignatureHttpClientBuilder(signatures, restClientBuilder))
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .strictTools(false)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
