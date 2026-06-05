package com.diplom.chatservice.llm;

public record LlmResponse(
        String content,
        Integer promptTokens,
        Integer completionTokens,
        String finishReason
) {
}
