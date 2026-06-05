package com.diplom.chatservice.llm;

import java.util.List;

public record LlmRequest(
        String system,
        List<LlmMessage> messages,
        Integer maxOutputTokens,
        Double temperature
) {
}
