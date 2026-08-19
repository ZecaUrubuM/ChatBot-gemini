package com.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades da memória de conversa, lidas de {@code chat.memory.*}.
 *
 * @param maxMessages quantidade máxima de mensagens retidas por sessão
 *                    (janela deslizante; as mais antigas são descartadas)
 */
@ConfigurationProperties(prefix = "chat.memory")
public record ChatMemoryProperties(int maxMessages) {

    public ChatMemoryProperties {
        if (maxMessages < 2) {
            throw new IllegalArgumentException("chat.memory.max-messages deve ser >= 2");
        }
    }
}
