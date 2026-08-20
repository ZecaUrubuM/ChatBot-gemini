package com.chatbot.config;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

/**
 * Intercepta o HTTP do {@code OpenAiChatModel} para round-trip de
 * {@code thought_signature} exigido pelo {@code gemini-3.6-flash} nas {@code @Tool}.
 */
final class ThoughtSignatureHttpClient implements HttpClient {

    private final HttpClient delegate;
    private final GeminiThoughtSignatureSupport signatures;

    ThoughtSignatureHttpClient(HttpClient delegate, GeminiThoughtSignatureSupport signatures) {
        this.delegate = delegate;
        this.signatures = signatures;
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
        String rewritten = signatures.injectOnRequest(body);
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
