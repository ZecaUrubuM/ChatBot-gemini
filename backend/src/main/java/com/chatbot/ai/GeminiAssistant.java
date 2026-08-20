package com.chatbot.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.spring.AiService;

/**
 * Contrato declarativo com o Gemini (padrão AI Service do LangChain4j).
 *
 * Não há implementação manual: o starter cria um proxy Spring que:
 *  1. injeta o {@code OpenAiChatModel} (endpoint Gemini OpenAI-compatible, bean em OpenAiGeminiChatModelConfig);
 *  2. anexa o histórico da sessão identificada por {@code @MemoryId};
 *  3. envia o System Prompt e a mensagem do usuário;
 *  4. registra automaticamente as tools {@code @Tool} (consulta ao catálogo SQL);
 *  5. executa function calling quando o modelo precisa de preço/estoque reais;
 *  6. devolve apenas o texto da resposta.
 *
 * {@link ChatMemoryAccess} permite consultar e evictar a memória de uma sessão
 * (útil para limpar o contexto ou inspecionar o histórico).
 */
@AiService
public interface GeminiAssistant extends ChatMemoryAccess {

    @SystemMessage(fromResource = "prompts/system-prompt.txt")
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);
}
