package com.diplom.chatservice.llm;

public interface LlmClient {
    LlmResponse complete(LlmRequest request);
}
