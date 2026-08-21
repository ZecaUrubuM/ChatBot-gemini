package com.chatbot.exception;

import com.chatbot.dto.ErrorResponse;
import com.chatbot.dto.RateLimitErrorResponse;
import dev.langchain4j.exception.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Traduz exceções de domínio e de validação em respostas HTTP consistentes.
 * Mantém o Controller livre de try/catch repetitivo (SRP).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return build(HttpStatus.BAD_REQUEST, "Parâmetros inválidos", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();

        return build(HttpStatus.BAD_REQUEST, "Parâmetros inválidos", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, "JSON inválido ou ausente", request, List.of());
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(
            SessionNotFoundException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler({RateLimitExceededException.class, RateLimitException.class})
    public ResponseEntity<RateLimitErrorResponse> handleRateLimit(RuntimeException ex) {
        log.warn("Cota da API Gemini esgotada: {}", ex.getMessage());
        return rateLimitBody();
    }

    @ExceptionHandler(GeminiIntegrationException.class)
    public ResponseEntity<?> handleGemini(
            GeminiIntegrationException ex,
            HttpServletRequest request
    ) {
        if (GeminiRateLimitDetector.matches(ex)) {
            log.warn("Cota da API Gemini esgotada (encapsulada): {}", ex.getMessage());
            return rateLimitBody();
        }
        log.error("Falha na integração com o Gemini: {}", ex.getMessage(), ex);
        return build(
                HttpStatus.BAD_GATEWAY,
                "Falha ao consultar o modelo de IA. Tente novamente em instantes.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        if (GeminiRateLimitDetector.matches(ex)) {
            log.warn("Cota da API Gemini esgotada (não mapeada): {}", ex.getMessage());
            return rateLimitBody();
        }
        log.error("Erro inesperado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor", request, List.of());
    }

    private ResponseEntity<RateLimitErrorResponse> rateLimitBody() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new RateLimitErrorResponse(
                        RateLimitExceededException.ERROR_CODE,
                        RateLimitExceededException.USER_MESSAGE
                ));
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            List<String> details
    ) {
        ErrorResponse body = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                Instant.now(),
                request.getRequestURI(),
                details
        );
        return ResponseEntity.status(status).body(body);
    }
}
