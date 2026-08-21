package com.chatbot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reescreve {@code messages} do payload OpenAI-compatible antes de ir ao Gemini.
 *
 * Garante a sequência:
 * system (no máximo uma, no início) → user → assistant(tool_calls) → tool → assistant.
 *
 * Remove system no meio do histórico, mensagens vazias, duplicatas consecutivas de user,
 * function call sem user/tool imediatamente antes, e o campo {@code content} em assistant
 * com {@code tool_calls} (o Gemini trata texto+functionCall como dois turnos).
 */
final class GeminiOpenAiMessageNormalizer {

    private static final Logger log = LoggerFactory.getLogger(GeminiOpenAiMessageNormalizer.class);

    private final ObjectMapper mapper;

    GeminiOpenAiMessageNormalizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String normalize(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode messagesNode = root.get("messages");
            if (messagesNode == null || !messagesNode.isArray()) {
                return body;
            }

            ArrayNode sanitized = sanitize((ArrayNode) messagesNode);
            if (messagesNode.equals(sanitized)) {
                return body;
            }
            if (root instanceof ObjectNode objectNode) {
                objectNode.set("messages", sanitized);
            }
            log.debug(
                    "Histórico OpenAI-compatible sanitizado para o Gemini: {} → {} mensagens",
                    messagesNode.size(),
                    sanitized.size()
            );
            return mapper.writeValueAsString(root);
        } catch (Exception ex) {
            log.warn("Falha ao normalizar messages do request OpenAI-compatible", ex);
            return body;
        }
    }

    ArrayNode sanitize(ArrayNode messages) {
        JsonNode system = null;
        List<JsonNode> rest = new ArrayList<>();

        for (JsonNode message : messages) {
            if (message == null || !message.isObject()) {
                continue;
            }
            String role = role(message);
            if ("system".equals(role)) {
                if (!isEmpty(message)) {
                    system = message.deepCopy();
                }
                continue;
            }
            if (isEmpty(message)) {
                continue;
            }
            ObjectNode copy = message.deepCopy();
            if ("assistant".equals(role) && hasToolCalls(copy)) {
                copy.remove("content");
            }
            rest.add(copy);
        }

        ArrayNode out = mapper.createArrayNode();
        if (system != null) {
            out.add(system);
        }

        int i = 0;
        while (i < rest.size()) {
            JsonNode message = rest.get(i);
            String role = role(message);

            if ("user".equals(role)) {
                int lastUser = i;
                while (lastUser + 1 < rest.size() && "user".equals(role(rest.get(lastUser + 1)))) {
                    lastUser++;
                }
                if (!out.isEmpty() && "user".equals(role(out.get(out.size() - 1)))) {
                    out.remove(out.size() - 1);
                }
                out.add(rest.get(lastUser));
                i = lastUser + 1;
                continue;
            }

            if ("assistant".equals(role) && hasToolCalls(message)) {
                if (!precedesFunctionCall(out)) {
                    i = skipFunctionCallGroup(rest, i);
                    continue;
                }
                Set<String> ids = toolCallIds(message);
                List<JsonNode> tools = new ArrayList<>();
                int j = i + 1;
                Set<String> seen = new LinkedHashSet<>();
                while (j < rest.size() && isTool(rest.get(j))) {
                    String toolCallId = toolCallId(rest.get(j));
                    if (toolCallId != null && ids.contains(toolCallId) && seen.add(toolCallId)) {
                        tools.add(rest.get(j));
                    } else if (toolCallId != null && !ids.contains(toolCallId)) {
                        break;
                    }
                    j++;
                }
                if (ids.isEmpty() || seen.size() < ids.size()) {
                    i = j;
                    continue;
                }
                out.add(message);
                tools.forEach(out::add);
                i = j;
                continue;
            }

            if ("assistant".equals(role)) {
                if (!out.isEmpty() && "assistant".equals(role(out.get(out.size() - 1)))
                        && !hasToolCalls(out.get(out.size() - 1))) {
                    out.remove(out.size() - 1);
                }
                if (precedesPlainAssistant(out)) {
                    out.add(message);
                }
                i++;
                continue;
            }

            i++;
        }

        return out;
    }

    private static boolean precedesFunctionCall(ArrayNode out) {
        if (out.isEmpty()) {
            return false;
        }
        JsonNode last = out.get(out.size() - 1);
        String role = role(last);
        return "user".equals(role) || isTool(last);
    }

    private static boolean precedesPlainAssistant(ArrayNode out) {
        if (out.isEmpty()) {
            return false;
        }
        JsonNode last = out.get(out.size() - 1);
        String role = role(last);
        return "user".equals(role) || isTool(last);
    }

    private static int skipFunctionCallGroup(List<JsonNode> rest, int i) {
        Set<String> ids = toolCallIds(rest.get(i));
        int j = i + 1;
        while (j < rest.size() && isTool(rest.get(j))) {
            String toolCallId = toolCallId(rest.get(j));
            if (toolCallId != null && !ids.contains(toolCallId)) {
                break;
            }
            j++;
        }
        return j;
    }

    private static boolean isEmpty(JsonNode message) {
        String role = role(message);
        if (isTool(message)) {
            return false;
        }
        if ("assistant".equals(role) && hasToolCalls(message)) {
            return false;
        }
        JsonNode content = message.get("content");
        if (content == null || content.isNull()) {
            return true;
        }
        if (content.isTextual()) {
            return content.asText().isBlank();
        }
        if (content.isArray()) {
            return content.isEmpty();
        }
        return false;
    }

    private static boolean hasToolCalls(JsonNode message) {
        JsonNode toolCalls = message.get("tool_calls");
        return toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty();
    }

    private static boolean isTool(JsonNode message) {
        String role = role(message);
        return "tool".equals(role) || "function".equals(role);
    }

    private static Set<String> toolCallIds(JsonNode message) {
        Set<String> ids = new LinkedHashSet<>();
        JsonNode toolCalls = message.get("tool_calls");
        if (toolCalls == null || !toolCalls.isArray()) {
            return ids;
        }
        for (JsonNode toolCall : toolCalls) {
            String id = text(toolCall, "id");
            if (id != null && !id.isBlank()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String toolCallId(JsonNode message) {
        String id = text(message, "tool_call_id");
        return id != null ? id : text(message, "toolCallId");
    }

    private static String role(JsonNode message) {
        String role = text(message, "role");
        return role == null ? "" : role.trim().toLowerCase();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
