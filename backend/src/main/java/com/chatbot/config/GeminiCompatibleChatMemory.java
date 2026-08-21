package com.chatbot.config;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * ChatMemory alinhado à ordem rígida do Gemini no function calling
 * (endpoint OpenAI-compatible):
 * <pre>
 *   SystemMessage (sempre no índice 0, no máximo uma)
 *   UserMessage
 *   AiMessage (ToolExecutionRequest)
 *   ToolExecutionResultMessage (retorno da tool / banco)
 *   AiMessage (resposta final)
 * </pre>
 *
 * O {@link dev.langchain4j.memory.chat.MessageWindowChatMemory} padrão evicta a
 * {@code UserMessage} mais antiga e deixa o {@code AiMessage} com tool_calls
 * logo após o system prompt — o Gemini responde 400
 * ("function call turn comes immediately after a user turn or after a function response turn").
 * Aqui a janela descarta o turno completo (user + tool loop + síntese) e
 * nunca envia function call órfã, system no meio da conversa ou mensagem vazia.
 */
final class GeminiCompatibleChatMemory implements ChatMemory {

    private final Object id;
    private final int maxMessages;
    private final LinkedList<ChatMessage> messages = new LinkedList<>();

    GeminiCompatibleChatMemory(Object id, int maxMessages) {
        this.id = Objects.requireNonNull(id, "id");
        if (maxMessages < 2) {
            throw new IllegalArgumentException("maxMessages deve ser >= 2");
        }
        this.maxMessages = maxMessages;
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public synchronized void add(ChatMessage message) {
        Objects.requireNonNull(message, "message");
        ChatMessage normalized = normalize(message);
        if (normalized == null) {
            return;
        }
        if (normalized instanceof SystemMessage systemMessage) {
            addSystem(systemMessage);
        } else {
            if (normalized instanceof UserMessage && lastIsIncompleteFunctionCall()) {
                dropTrailingIncompleteFunctionCall();
            }
            if (normalized instanceof UserMessage && lastIsUser()) {
                messages.removeLast();
            }
            messages.add(normalized);
        }
        ensureCapacity();
        dropLeadingOrphans();
    }

    @Override
    public synchronized List<ChatMessage> messages() {
        ensureCapacity();
        dropLeadingOrphans();
        return List.copyOf(messages);
    }

    @Override
    public synchronized void clear() {
        messages.clear();
    }

    private void addSystem(SystemMessage incoming) {
        int existingIndex = indexOfSystem();
        if (existingIndex >= 0) {
            if (messages.get(existingIndex).equals(incoming)) {
                return;
            }
            messages.remove(existingIndex);
        }
        messages.addFirst(incoming);
    }

    private ChatMessage normalize(ChatMessage message) {
        if (isBlank(message)) {
            return null;
        }
        if (message instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
            // Texto/thinking no mesmo turno do function call vira um "model turn"
            // extra no Gemini: a function call deixa de vir logo após user/tool.
            return ai.toBuilder()
                    .text(null)
                    .thinking(null)
                    .build();
        }
        return message;
    }

    private void ensureCapacity() {
        while (messages.size() > maxMessages && canEvictOlderTurn()) {
            evictTurnStartingAt(firstEvictableIndex());
        }
    }

    private boolean canEvictOlderTurn() {
        int lastUser = lastUserIndex();
        int firstEvictable = firstEvictableIndex();
        return firstEvictable >= 0 && lastUser >= 0 && firstEvictable < lastUser;
    }

    private void evictTurnStartingAt(int index) {
        if (index < 0 || index >= messages.size()) {
            return;
        }
        ChatMessage first = messages.remove(index);
        if (first instanceof UserMessage) {
            while (index < messages.size() && isFunctionCall(messages.get(index))) {
                messages.remove(index);
                while (index < messages.size() && messages.get(index) instanceof ToolExecutionResultMessage) {
                    messages.remove(index);
                }
            }
            if (index < messages.size() && isPlainAi(messages.get(index))) {
                messages.remove(index);
            }
            return;
        }
        if (isFunctionCall(first)) {
            while (index < messages.size() && messages.get(index) instanceof ToolExecutionResultMessage) {
                messages.remove(index);
            }
            if (index < messages.size() && isPlainAi(messages.get(index))) {
                messages.remove(index);
            }
        }
    }

    private void dropLeadingOrphans() {
        int index = firstEvictableIndex();
        while (index >= 0 && index < messages.size() && !(messages.get(index) instanceof UserMessage)) {
            evictTurnStartingAt(index);
            index = firstEvictableIndex();
        }
    }

    private boolean lastIsIncompleteFunctionCall() {
        return !messages.isEmpty() && isFunctionCall(messages.getLast());
    }

    private void dropTrailingIncompleteFunctionCall() {
        while (!messages.isEmpty() && isFunctionCall(messages.getLast())) {
            messages.removeLast();
        }
    }

    private boolean lastIsUser() {
        return !messages.isEmpty() && messages.getLast() instanceof UserMessage;
    }

    private int indexOfSystem() {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof SystemMessage) {
                return i;
            }
        }
        return -1;
    }

    private int firstEvictableIndex() {
        if (messages.isEmpty()) {
            return -1;
        }
        return messages.getFirst() instanceof SystemMessage ? (messages.size() > 1 ? 1 : -1) : 0;
    }

    private int lastUserIndex() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isFunctionCall(ChatMessage message) {
        return message instanceof AiMessage ai && ai.hasToolExecutionRequests();
    }

    private static boolean isPlainAi(ChatMessage message) {
        return message instanceof AiMessage ai && !ai.hasToolExecutionRequests();
    }

    private static boolean isBlank(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return isBlankText(systemMessage.text());
        }
        if (message instanceof UserMessage userMessage) {
            if (!userMessage.hasSingleText()) {
                return userMessage.contents() == null || userMessage.contents().isEmpty();
            }
            return isBlankText(userMessage.singleText());
        }
        if (message instanceof AiMessage aiMessage) {
            if (aiMessage.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
                return requests == null || requests.isEmpty();
            }
            return isBlankText(aiMessage.text());
        }
        return false;
    }

    private static boolean isBlankText(String text) {
        return text == null || text.isBlank();
    }
}
