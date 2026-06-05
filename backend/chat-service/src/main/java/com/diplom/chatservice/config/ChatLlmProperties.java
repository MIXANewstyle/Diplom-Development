package com.diplom.chatservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat.llm")
public record ChatLlmProperties(
        String provider,
        String baseUrl,
        String model,
        String apiKey,
        int maxOutputTokens,
        double temperature,
        long requestTimeoutMs,
        int maxRetries,
        int promptTokenBudget,
        int hardTurnCap,
        Prompts prompts
) {
    public record Prompts(
            String pairedSystem,
            String soloSystem,
            String contextBlockTemplate,
            String summarization
    ) {}
}
