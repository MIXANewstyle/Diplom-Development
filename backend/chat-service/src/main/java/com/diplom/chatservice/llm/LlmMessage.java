package com.diplom.chatservice.llm;

/**
 * One conversation message. {@code cacheBoundary} marks the end of a stable prefix that
 * providers with explicit prompt caching (Anthropic via OpenRouter, Anthropic direct) may cache.
 */
public record LlmMessage(
        String role,
        String content,
        boolean cacheBoundary
) {
    public LlmMessage(String role, String content) {
        this(role, content, false);
    }
}
