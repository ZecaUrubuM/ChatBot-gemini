package com.chatbot.exception;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Identifica o HTTP 429 (cota / rate limit) em qualquer ponto da cadeia de causas
 * do LangChain4j: {@link RateLimitException}, {@link HttpException} com status 429,
 * {@code InvalidRequestException} ou {@link RuntimeException} cujo código/mensagem
 * indique quota excedida.
 */
public final class GeminiRateLimitDetector {

    private static final Pattern RATE_LIMIT_HINT = Pattern.compile(
            "\\b429\\b|rate[\\s_-]?limit|too many requests|resource[_\\s-]?exhausted|quota[\\s_-]?(exceeded|exhausted)|current quota",
            Pattern.CASE_INSENSITIVE
    );

    private static final String[] STATUS_ACCESSORS = {
            "statusCode", "getStatusCode", "code", "getCode", "status", "getStatus"
    };

    private GeminiRateLimitDetector() {
    }

    public static boolean matches(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof RateLimitException) {
                return true;
            }
            if (current instanceof HttpException httpException && httpException.statusCode() == 429) {
                return true;
            }
            if (statusCodeOf(current) == 429) {
                return true;
            }
            if (looksLikeRateLimit(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeRateLimit(String message) {
        return message != null && RATE_LIMIT_HINT.matcher(message).find();
    }

    private static int statusCodeOf(Throwable throwable) {
        for (String accessor : STATUS_ACCESSORS) {
            try {
                Method method = throwable.getClass().getMethod(accessor);
                Object value = method.invoke(throwable);
                Integer code = asStatusCode(value);
                if (code != null) {
                    return code;
                }
            } catch (ReflectiveOperationException ignored) {
                // acessor inexistente nesta classe da cadeia
            }
        }
        return -1;
    }

    private static Integer asStatusCode(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                String lower = text.toLowerCase(Locale.ROOT);
                if (lower.contains("429") || lower.contains("too_many_requests")) {
                    return 429;
                }
            }
        }
        return null;
    }
}
