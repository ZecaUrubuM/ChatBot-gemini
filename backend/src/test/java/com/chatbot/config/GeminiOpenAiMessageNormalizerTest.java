package com.chatbot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiOpenAiMessageNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiOpenAiMessageNormalizer normalizer = new GeminiOpenAiMessageNormalizer(mapper);

    @Test
    void moveSystemParaOInicioERemoveDuplicataNoMeio() throws Exception {
        String rewritten = normalizer.normalize("""
                {
                  "messages": [
                    { "role": "user", "content": "preço do arroz" },
                    { "role": "system", "content": "Você é o assistente." },
                    {
                      "role": "assistant",
                      "content": "vou consultar",
                      "tool_calls": [{
                        "id": "call-1",
                        "type": "function",
                        "function": { "name": "buscarProdutosPorNome", "arguments": "{}" }
                      }]
                    },
                    { "role": "tool", "tool_call_id": "call-1", "content": "Arroz R$ 5,00" }
                  ]
                }
                """);

        JsonNode messages = mapper.readTree(rewritten).get("messages");
        assertEquals("system", messages.get(0).get("role").asText());
        assertEquals("user", messages.get(1).get("role").asText());
        assertEquals("assistant", messages.get(2).get("role").asText());
        assertFalse(messages.get(2).has("content"));
        assertEquals("tool", messages.get(3).get("role").asText());
    }

    @Test
    void removeContentDeAssistantComToolCalls() throws Exception {
        String rewritten = normalizer.normalize("""
                {
                  "messages": [
                    { "role": "user", "content": "preço do arroz" },
                    {
                      "role": "assistant",
                      "content": "Vou buscar no catálogo.",
                      "tool_calls": [{
                        "id": "call-1",
                        "type": "function",
                        "function": { "name": "buscarProdutosPorNome", "arguments": "{}" }
                      }]
                    },
                    { "role": "tool", "tool_call_id": "call-1", "content": "Arroz R$ 5,00" }
                  ]
                }
                """);

        JsonNode assistant = mapper.readTree(rewritten).at("/messages/1");
        assertEquals("assistant", assistant.get("role").asText());
        assertTrue(assistant.has("tool_calls"));
        assertFalse(assistant.has("content"));
    }

    @Test
    void descartaFunctionCallOrfaAposSystem() throws Exception {
        String rewritten = normalizer.normalize("""
                {
                  "messages": [
                    { "role": "system", "content": "Você é o assistente." },
                    {
                      "role": "assistant",
                      "tool_calls": [{
                        "id": "call-old",
                        "type": "function",
                        "function": { "name": "buscarProdutosPorNome", "arguments": "{}" }
                      }]
                    },
                    { "role": "tool", "tool_call_id": "call-old", "content": "legado" },
                    { "role": "assistant", "content": "O arroz custa R$ 5,00." },
                    { "role": "user", "content": "e o feijão?" }
                  ]
                }
                """);

        ArrayNode messages = (ArrayNode) mapper.readTree(rewritten).get("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        assertEquals("user", messages.get(1).get("role").asText());
        assertEquals("e o feijão?", messages.get(1).get("content").asText());
    }

    @Test
    void removeMensagensVaziasEUserDuplicado() throws Exception {
        String rewritten = normalizer.normalize("""
                {
                  "messages": [
                    { "role": "system", "content": "Você é o assistente." },
                    { "role": "user", "content": "" },
                    { "role": "assistant", "content": "   " },
                    { "role": "user", "content": "oi" },
                    { "role": "user", "content": "preço do arroz" }
                  ]
                }
                """);

        ArrayNode messages = (ArrayNode) mapper.readTree(rewritten).get("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        assertEquals("preço do arroz", messages.get(1).get("content").asText());
    }

    @Test
    void preservaTurnoValidoDeFunctionCalling() throws Exception {
        String body = """
                {
                  "messages": [
                    { "role": "system", "content": "Você é o assistente." },
                    { "role": "user", "content": "preço do arroz" },
                    {
                      "role": "assistant",
                      "tool_calls": [{
                        "id": "call-1",
                        "type": "function",
                        "function": { "name": "buscarProdutosPorNome", "arguments": "{\\"termo\\":\\"arroz\\"}" }
                      }]
                    },
                    { "role": "tool", "tool_call_id": "call-1", "content": "Arroz R$ 5,00" }
                  ]
                }
                """;

        String rewritten = normalizer.normalize(body);
        ArrayNode messages = (ArrayNode) mapper.readTree(rewritten).get("messages");
        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        assertEquals("user", messages.get(1).get("role").asText());
        assertEquals("assistant", messages.get(2).get("role").asText());
        assertEquals("tool", messages.get(3).get("role").asText());
    }

    @Test
    void descartaFunctionCallSemToolResultCorrespondente() throws Exception {
        String rewritten = normalizer.normalize("""
                {
                  "messages": [
                    { "role": "user", "content": "preço do arroz" },
                    {
                      "role": "assistant",
                      "tool_calls": [{
                        "id": "call-1",
                        "type": "function",
                        "function": { "name": "buscarProdutosPorNome", "arguments": "{}" }
                      }]
                    },
                    { "role": "user", "content": "ainda aí?" }
                  ]
                }
                """);

        ArrayNode messages = (ArrayNode) mapper.readTree(rewritten).get("messages");
        assertEquals(1, messages.size());
        assertEquals("ainda aí?", messages.get(0).get("content").asText());
    }
}
