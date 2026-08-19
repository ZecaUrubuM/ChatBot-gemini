package com.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação.
 *
 * {@code @SpringBootApplication} habilita:
 *  - varredura de componentes no pacote {@code com.chatbot} e subpacotes;
 *  - auto-configuração do Spring Boot, JPA e dos starters LangChain4j.
 */
@SpringBootApplication
public class ChatBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatBotApplication.class, args);
    }
}
