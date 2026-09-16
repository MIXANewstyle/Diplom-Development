package com.diplom.chatservice.llm;

/**
 * A text segment of the system prompt. {@code cacheBoundary} = a cache breakpoint may be placed after it.
 */
public record LlmBlock(
        String text,
        boolean cacheBoundary
) {
    public static LlmBlock of(String text) {
        return new LlmBlock(text, false);
    }

    public static LlmBlock cached(String text) {
        return new LlmBlock(text, true);
    }
}
