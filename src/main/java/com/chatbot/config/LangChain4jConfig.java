package com.chatbot.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração da memória de chat do LangChain4j.
 *
 * O {@link ChatMemoryProvider} é detectado automaticamente pelo
 * {@code langchain4j-spring-boot-starter} e injetado em toda interface
 * anotada com {@code @AiService}. Cada {@code memoryId} (nosso sessionId)
 * recebe uma instância isolada de {@link MessageWindowChatMemory}.
 *
 * A persistência é in-memory: o histórico some se a aplicação reiniciar.
 * Para produção, implemente {@code ChatMemoryStore} (Redis, banco, etc.).
 */
@Configuration
@EnableConfigurationProperties(ChatMemoryProperties.class)
public class LangChain4jConfig {

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryProperties properties) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(properties.maxMessages())
                .build();
    }
}
