package com.diplom.chatservice.llm;

import java.math.BigDecimal;

public record LlmResponse(
        String content,
        Integer promptTokens,
        Integer completionTokens,
        String finishReason,
        Integer cachedPromptTokens,
        BigDecimal costUsd
) {
    public LlmResponse(String content, Integer promptTokens, Integer completionTokens, String finishReason) {
        this(content, promptTokens, completionTokens, finishReason, null, null);
    }

    public int totalTokens() {
        return (promptTokens != null ? promptTokens : 0) + (completionTokens != null ? completionTokens : 0);
    }
}
