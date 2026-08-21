package com.chatbot.config;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

/**
 * Intercepta o HTTP do {@code OpenAiChatModel}: normaliza a ordem das
 * {@code messages} (Gemini function calling) e faz o round-trip de
 * {@code thought_signature} exigido pelo {@code gemini-3.6-flash} nas {@code @Tool}.
 */
final class ThoughtSignatureHttpClient implements HttpClient {

    private final HttpClient delegate;
    private final GeminiThoughtSignatureSupport signatures;
    private final GeminiOpenAiMessageNormalizer messageNormalizer;

    ThoughtSignatureHttpClient(
            HttpClient delegate,
            GeminiThoughtSignatureSupport signatures,
            GeminiOpenAiMessageNormalizer messageNormalizer
    ) {
        this.delegate = delegate;
        this.signatures = signatures;
        this.messageNormalizer = messageNormalizer;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        SuccessfulHttpResponse response = delegate.execute(withSignatures(request));
        signatures.captureFromResponse(response.body());
        return response;
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        delegate.execute(withSignatures(request), parser, listener);
    }

    private HttpRequest withSignatures(HttpRequest request) {
        String body = request.body();
        if (body == null || body.isBlank()) {
            return request;
        }
        String rewritten = messageNormalizer.normalize(body);
        rewritten = signatures.injectOnRequest(rewritten);
        if (rewritten.equals(body)) {
            return request;
        }
        return HttpRequest.builder()
                .method(request.method())
                .url(request.url())
                .headers(request.headers())
                .body(rewritten)
                .build();
    }
}
