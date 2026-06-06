package com.diplom.chatservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat.limits")
public record ChatLimitsProperties(
        int turnsPerMinute,
        int dailyTokenBudget,
        int concurrentActiveRooms
) {}
