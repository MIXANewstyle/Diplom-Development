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
        boolean logPayload,
        int promptTokenBudget,
        int hardTurnCap,
        ContextProps context,
        ExecutorProps executor,
        Prompts prompts,
        Models models,
        OpenRouterProps openrouter,
        DiaryProps diary,
        EmbeddingsProps embeddings
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
            String summarization,
            String diarySystem,
            String diaryDaySummary,
            String diaryWeekSummary,
            String diaryMonthSummary,
            String diaryFactExtraction
    ) {}

    /**
     * Per-role model overrides. Empty/null value = fall back to {@code chat.llm.model}.
     */
    public record Models(
            String diary,
            String summary,
            String facts
    ) {
        public String diaryOrNull() { return blankToNull(diary); }
        public String summaryOrNull() { return blankToNull(summary); }
        public String factsOrNull() { return blankToNull(facts); }

        private static String blankToNull(String s) {
            return s == null || s.isBlank() ? null : s;
        }
    }

    /**
     * OpenRouter-specific request extensions (all safe to disable for other providers).
     */
    public record OpenRouterProps(
            boolean usageAccounting,
            boolean cacheControl,
            String appTitle
    ) {}

    public record DiaryProps(
            int promptTokenBudget,
            int maxOutputTokens,
            int hardTurnCap,
            int dailyTokenBudget,
            int ragTopK,
            double ragMinScore,
            int ragMaxPerDay,
            int recentDays,
            int maxFacts,
            int yesterdayVerbatimTurns
    ) {}

    public record EmbeddingsProps(
            String baseUrl,
            String apiKey,
            String model,
            int dimensions,
            int batchSize,
            long requestTimeoutMs
    ) {}
}
