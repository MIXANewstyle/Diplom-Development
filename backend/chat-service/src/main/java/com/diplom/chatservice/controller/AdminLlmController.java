package com.diplom.chatservice.controller;

import com.diplom.chatservice.llm.LlmClient;
import com.diplom.chatservice.llm.LlmMessage;
import com.diplom.chatservice.llm.LlmRequest;
import com.diplom.chatservice.llm.LlmResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/admin/llm")
@RequiredArgsConstructor
public class AdminLlmController {

    private final LlmClient llmClient;

    @Value("${chat.llm.max-output-tokens}")
    private Integer defaultMaxOutputTokens;

    @Value("${chat.llm.temperature}")
    private Double defaultTemperature;

    // Diagnostic endpoint to verify LLM connectivity.
    // May be removed before production.
    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public LlmResponse testLlm(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "Hello");

        LlmRequest request = new LlmRequest(
                "Ты — ассистент для проверки связи.",
                List.of(new LlmMessage("user", prompt)),
                defaultMaxOutputTokens,
                defaultTemperature
        );

        return llmClient.complete(request);
    }
}
