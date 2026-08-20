package com.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Aplica as origens de {@code app.cors.allowed-origins} no Spring MVC.
 * Sem esta classe a propriedade no {@code application.properties} não tem efeito.
 *
 * O front deve ser servido via HTTP (ex.: {@code http://localhost:5173}),
 * não como {@code file://}, cuja origem é {@code "null"} e é bloqueada pelo CORS.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String origens) {
        this.allowedOrigins = Arrays.stream(origens.split(","))
                .map(String::trim)
                .filter(origem -> !origem.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("*")
                .allowedHeaders("*");
    }
}
