package com.chatbot.config;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiCompatibleChatMemoryTest {

    private static final SystemMessage SYSTEM = SystemMessage.from("Você é o assistente do mercado.");

    @Test
    void systemFicaSempreNoInicioENaoDuplica() {
        GeminiCompatibleChatMemory memory = new GeminiCompatibleChatMemory("s1", 20);
        memory.add(SYSTEM);
        memory.add(UserMessage.from("oi"));
        memory.add(AiMessage.from("olá"));
        memory.add(SYSTEM);

        List<ChatMessage> messages = memory.messages();
        assertEquals(3, messages.size());
        assertEquals(SYSTEM, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertInstanceOf(AiMessage.class, messages.get(2));
    }

    @Test
    void systemNovoSubstituiOAnteriorNoIndiceZero() {
        GeminiCompatibleChatMemory memory = new GeminiCompatibleChatMemory("s1", 20);
        memory.add(SYSTEM);
        memory.add(UserMessage.from("oi"));
        memory.add(AiMessage.from("olá"));
        memory.add(SystemMessage.from("Novo system"));

        List<ChatMessage> messages = memory.messages();
        assertEquals(3, messages.size());
        assertEquals("Novo system", ((SystemMessage) messages.get(0)).text());
        assertInstanceOf(UserMessage.class, messages.get(1));
    }

    @Test
    void ignoraUserVazioEAiSemTextoNemTool() {
        GeminiCompatibleChatMemory memory = new GeminiCompatibleChatMemory("s1", 20);
        memory.add(SYSTEM);
        memory.add(UserMessage.from("   "));
        memory.add(AiMessage.from("   "));
        memory.add(UserMessage.from("preço do arroz"));

        List<ChatMessage> messages = memory.messages();
        assertEquals(2, messages.size());
        assertEquals("preço do arroz", ((UserMessage) messages.get(1)).singleText());
    }

    @Test
    void removeTextoDoAiMessageComToolCall() {
        GeminiCompatibleChatMemory memory = new GeminiCompatibleChatMemory("s1", 20);
        memory.add(SYSTEM);
        memory.add(UserMessage.from("preço do arroz"));
        memory.add(AiMessage.from("vou consultar", List.of(toolCall("call-1"))));

        AiMessage stored = (AiMessage) memory.messages().get(2);
        assertTrue(stored.hasToolExecutionRequests());
        assertNull(stored.text());
    }

    @Test
    void sequenciaDeFunctionCallingFicaIntacta() {
        GeminiCompatibleChatMemory memory = new GeminiCompatibleChatMemory("s1", 20);
        addCompleteToolTurn(memory, "preço do arroz", "call-1", "R$ 5,00", "O arroz custa R$ 5,00.");

        List<ChatMessage> messages = memory.messages();
        assertEquals(5, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertTrue(((AiMessage) messages.get(2)).hasToolExecutionRequests());
        assertInstanceOf(ToolExecutionResultMessage.class, messages.get(3));
        assertEquals("O arroz custa R$ 5,00.", ((AiMessage) messages.get(4)).text());
    }

    @Test
    void evictionRemoveTurnoCompletoENaoDeixaFunctionCallAposSystem() {
        GeminiCompatibleChatMemory memory = new GeminiCompatibleChatMemory("s1", 5);
        addCompleteToolTurn(memory, "preço do arroz", "call-1", "R$ 5,00", "O arroz custa R$ 5,00.");
        memory.add(UserMessage.from("e o feijão?"));

        List<ChatMessage> messages = memory.messages();
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertEquals("e o feijão?", ((UserMessage) messages.get(1)).singleText());
        assertFalse(messages.stream().anyMatch(m -> m instanceof AiMessage ai && ai.hasToolExecutionRequests()));
        assertFalse(messages.stream().anyMatch(ToolExecutionResultMessage.class::isInstance));
    }

    @Test
    void naoEvictaOTurnoCorrenteMesmoAcimaDoMax() {
        GeminiCompatibleChatMemory memory = new GeminiCompatibleChatMemory("s1", 4);
        addCompleteToolTurn(memory, "preço do arroz", "call-1", "R$ 5,00", "O arroz custa R$ 5,00.");

        List<ChatMessage> messages = memory.messages();
        assertEquals(5, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertTrue(((AiMessage) messages.get(2)).hasToolExecutionRequests());
        assertInstanceOf(ToolExecutionResultMessage.class, messages.get(3));
        assertInstanceOf(AiMessage.class, messages.get(4));
    }

    @Test
    void retryComUserAposToolCallIncompletoDescartaAFunctionCallOrfa() {
        GeminiCompatibleChatMemory memory = new GeminiCompatibleChatMemory("s1", 20);
        memory.add(SYSTEM);
        memory.add(UserMessage.from("preço do arroz"));
        memory.add(AiMessage.from(toolCall("call-1")));
        memory.add(UserMessage.from("preço do arroz"));

        List<ChatMessage> messages = memory.messages();
        assertEquals(2, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertEquals("preço do arroz", ((UserMessage) messages.get(1)).singleText());
    }

    @Test
    void colapsaUserConsecutivo() {
        GeminiCompatibleChatMemory memory = new GeminiCompatibleChatMemory("s1", 20);
        memory.add(SYSTEM);
        memory.add(UserMessage.from("oi"));
        memory.add(UserMessage.from("olá de novo"));

        List<ChatMessage> messages = memory.messages();
        assertEquals(2, messages.size());
        assertEquals("olá de novo", ((UserMessage) messages.get(1)).singleText());
    }

    private static void addCompleteToolTurn(
            GeminiCompatibleChatMemory memory,
            String user,
            String callId,
            String toolResult,
            String finalAnswer
    ) {
        memory.add(SYSTEM);
        memory.add(UserMessage.from(user));
        memory.add(AiMessage.from(toolCall(callId)));
        memory.add(ToolExecutionResultMessage.from(callId, "buscarProdutosPorNome", toolResult));
        memory.add(AiMessage.from(finalAnswer));
    }

    private static ToolExecutionRequest toolCall(String id) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("buscarProdutosPorNome")
                .arguments("{\"termo\":\"arroz\"}")
                .build();
    }
}
