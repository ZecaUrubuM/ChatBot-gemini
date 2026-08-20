package com.chatbot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * O {@code gemini-3.6-flash} exige {@code thought_signature} em cada function call
 * no loop de tools. O {@code OpenAiChatModel} do LangChain4j ignora
 * {@code extra_content} ({@code @JsonIgnoreProperties(ignoreUnknown = true)}),
 * então a assinatura some no round-trip.
 *
 * {@code thinking_budget: 0} é da geração 2.5 e o Gemini 3 rejeita / ignora.
 * Mesmo {@code thinking_level=minimal} continua exigindo a assinatura.
 *
 * Esta classe: (1) guarda a assinatura real da resposta; (2) reinsere em
 * {@code tool_calls[].extra_content.google.thought_signature}; (3) se não houver
 * cache, usa o sentinel oficial {@code skip_thought_signature_validator}.
 */
final class GeminiThoughtSignatureSupport {

    static final String SKIP_VALIDATOR = "skip_thought_signature_validator";

    private static final Logger log = LoggerFactory.getLogger(GeminiThoughtSignatureSupport.class);
    private static final int MAX_CACHE = 2048;

    private final ObjectMapper mapper;
    private final Map<String, String> signaturesByToolCallId;

    GeminiThoughtSignatureSupport(ObjectMapper mapper) {
        this.mapper = mapper;
        this.signaturesByToolCallId = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > MAX_CACHE;
            }
        });
    }

    String injectOnRequest(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode messages = root.get("messages");
            if (messages == null || !messages.isArray()) {
                return body;
            }

            boolean changed = false;
            for (JsonNode message : messages) {
                if (!"assistant".equals(text(message, "role"))) {
                    continue;
                }
                JsonNode toolCalls = message.get("tool_calls");
                if (toolCalls == null || !toolCalls.isArray()) {
                    continue;
                }
                for (JsonNode toolCall : toolCalls) {
                    if (toolCall instanceof ObjectNode objectNode && injectOnToolCall(objectNode)) {
                        changed = true;
                    }
                }
            }
            return changed ? mapper.writeValueAsString(root) : body;
        } catch (Exception ex) {
            log.warn("Falha ao injetar thought_signature no request OpenAI-compatible", ex);
            return body;
        }
    }

    void captureFromResponse(String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray()) {
                return;
            }
            for (JsonNode choice : choices) {
                JsonNode message = choice.get("message");
                if (message == null) {
                    continue;
                }
                JsonNode toolCalls = message.get("tool_calls");
                if (toolCalls == null || !toolCalls.isArray()) {
                    continue;
                }
                for (JsonNode toolCall : toolCalls) {
                    cacheFromToolCall(toolCall);
                }
            }
        } catch (Exception ex) {
            log.debug("Falha ao ler thought_signature da resposta Gemini", ex);
        }
    }

    private boolean injectOnToolCall(ObjectNode toolCall) {
        String existing = nestedSignature(toolCall);
        String id = text(toolCall, "id");
        if (existing != null && !existing.isBlank()) {
            if (id != null) {
                signaturesByToolCallId.put(id, existing);
            }
            return false;
        }

        String signature = id != null ? signaturesByToolCallId.get(id) : null;
        if (signature == null || signature.isBlank()) {
            signature = SKIP_VALIDATOR;
            log.debug("thought_signature ausente no tool_call id={}; usando sentinel da Google", id);
        }

        ObjectNode google = mapper.createObjectNode();
        google.put("thought_signature", signature);
        ObjectNode extra = mapper.createObjectNode();
        extra.set("google", google);
        toolCall.set("extra_content", extra);
        return true;
    }

    private void cacheFromToolCall(JsonNode toolCall) {
        String id = text(toolCall, "id");
        String signature = nestedSignature(toolCall);
        if (id != null && signature != null && !signature.isBlank()) {
            signaturesByToolCallId.put(id, signature);
        }
    }

    private static String nestedSignature(JsonNode toolCall) {
        JsonNode extra = toolCall.get("extra_content");
        if (extra == null) {
            return null;
        }
        JsonNode google = extra.get("google");
        if (google == null) {
            return null;
        }
        return text(google, "thought_signature");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
