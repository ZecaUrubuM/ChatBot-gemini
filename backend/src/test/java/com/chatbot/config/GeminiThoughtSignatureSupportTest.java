package com.chatbot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiThoughtSignatureSupportTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiThoughtSignatureSupport support = new GeminiThoughtSignatureSupport(mapper);

    @Test
    void reusaAssinaturaRealDoResponseNoProximoRequest() throws Exception {
        support.captureFromResponse("""
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "tool_calls": [{
                        "id": "call-arroz",
                        "type": "function",
                        "function": { "name": "buscarProdutosPorNome", "arguments": "{\\"termo\\":\\"arroz\\"}" },
                        "extra_content": { "google": { "thought_signature": "ASSINATURA-REAL" } }
                      }]
                    }
                  }]
                }
                """);

        String rewritten = support.injectOnRequest("""
                {
                  "model": "gemini-3.6-flash",
                  "messages": [{
                    "role": "assistant",
                    "tool_calls": [{
                      "id": "call-arroz",
                      "type": "function",
                      "function": { "name": "buscarProdutosPorNome", "arguments": "{\\"termo\\":\\"arroz\\"}" }
                    }]
                  }]
                }
                """);

        JsonNode signature = mapper.readTree(rewritten)
                .at("/messages/0/tool_calls/0/extra_content/google/thought_signature");
        assertEquals("ASSINATURA-REAL", signature.asText());
    }

    @Test
    void usaSentinelQuandoNaoHaAssinaturaEmCache() throws Exception {
        String rewritten = support.injectOnRequest("""
                {
                  "messages": [{
                    "role": "assistant",
                    "tool_calls": [{
                      "id": "call-sem-cache",
                      "type": "function",
                      "function": { "name": "buscarProdutosPorNome", "arguments": "{}" }
                    }]
                  }]
                }
                """);

        JsonNode signature = mapper.readTree(rewritten)
                .at("/messages/0/tool_calls/0/extra_content/google/thought_signature");
        assertEquals(GeminiThoughtSignatureSupport.SKIP_VALIDATOR, signature.asText());
        assertTrue(rewritten.contains("extra_content"));
    }
}
