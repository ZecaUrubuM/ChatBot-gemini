package com.chatbot.config;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.spring.restclient.SpringRestClient;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HttpClientBuilder que envolve o Spring RestClient e injeta thought_signature
 * no payload OpenAI-compatible da Google.
 */
final class ThoughtSignatureHttpClientBuilder implements HttpClientBuilder {

    private final HttpClientBuilder delegate;
    private final GeminiThoughtSignatureSupport signatures;
    private final GeminiOpenAiMessageNormalizer messageNormalizer;

    ThoughtSignatureHttpClientBuilder(
            GeminiThoughtSignatureSupport signatures,
            GeminiOpenAiMessageNormalizer messageNormalizer,
            RestClient.Builder restClientBuilder
    ) {
        this.delegate = SpringRestClient.builder()
                .restClientBuilder(restClientBuilder.clone())
                .createDefaultStreamingRequestExecutor(false);
        this.signatures = signatures;
        this.messageNormalizer = messageNormalizer;
    }

    @Override
    public Duration connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration timeout) {
        delegate.connectTimeout(timeout);
        return this;
    }

    @Override
    public Duration readTimeout() {
        return delegate.readTimeout();
    }

    @Override
    public HttpClientBuilder readTimeout(Duration timeout) {
        delegate.readTimeout(timeout);
        return this;
    }

    @Override
    public HttpClient build() {
        return new ThoughtSignatureHttpClient(delegate.build(), signatures, messageNormalizer);
    }
}
