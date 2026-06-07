package com.diplom.chatservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "chat.guest")
public record ChatGuestProperties(
        Duration tokenTtl,
        Map<Integer, String> genderLabels
) {}
