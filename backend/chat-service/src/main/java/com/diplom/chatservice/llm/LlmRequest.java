package com.diplom.chatservice.llm;

import java.util.List;

/**
 * Provider-independent completion request.
 *
 * <p>{@code system} is a list of blocks so that stable content (instructions, persona, summaries)
 * can be separated from volatile content and cache breakpoints can be expressed per block.
 * {@code model} is optional: {@code null} means the client's default model.
 */
public record LlmRequest(
        List<LlmBlock> system,
        List<LlmMessage> messages,
        Integer maxOutputTokens,
        Double temperature,
        String model
) {
    /** Backwards-compatible form: a single plain system string, default model. */
    public LlmRequest(String system, List<LlmMessage> messages, Integer maxOutputTokens, Double temperature) {
        this(system == null || system.isBlank() ? List.of() : List.of(LlmBlock.of(system)),
                messages, maxOutputTokens, temperature, null);
    }

    /** Single plain system string with an explicit model. */
    public LlmRequest(String system, List<LlmMessage> messages, Integer maxOutputTokens, Double temperature, String model) {
        this(system == null || system.isBlank() ? List.of() : List.of(LlmBlock.of(system)),
                messages, maxOutputTokens, temperature, model);
    }

    /** The whole system prompt as one string (blocks joined by a blank line). */
    public String systemText() {
        if (system == null || system.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (LlmBlock b : system) {
            if (b.text() == null || b.text().isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(b.text());
        }
        return sb.toString();
    }

    public boolean hasCacheBoundaries() {
        if (system != null && system.stream().anyMatch(LlmBlock::cacheBoundary)) return true;
        return messages != null && messages.stream().anyMatch(LlmMessage::cacheBoundary);
    }
}
