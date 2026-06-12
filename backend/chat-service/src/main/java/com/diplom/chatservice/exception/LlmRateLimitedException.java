package com.diplom.chatservice.exception;

import lombok.Getter;

@Getter
public class LlmRateLimitedException extends RuntimeException {

    private final Long retryAfterSeconds;

    public LlmRateLimitedException(String message, Long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public LlmRateLimitedException(String message) {
        super(message);
        this.retryAfterSeconds = null;
    }
}
