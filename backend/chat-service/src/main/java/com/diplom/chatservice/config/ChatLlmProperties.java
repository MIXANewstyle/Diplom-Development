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
        ContextProps context,
        ExecutorProps executor,
        Prompts prompts
) {
    public record ContextProps(
            int aboutMaxChars,
            int recentTurnsVerbatim
    ) {}

    public record ExecutorProps(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity
    ) {}

    public record Prompts(
            String pairedSystem,
            String soloSystem,
            String contextBlockTemplate,
            String summarization
    ) {}
}
