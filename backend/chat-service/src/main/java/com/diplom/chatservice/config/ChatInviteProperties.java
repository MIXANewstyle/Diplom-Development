package com.diplom.chatservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "chat.invite")
public record ChatInviteProperties(
        Duration ttl,
        String publicBaseUrl
) {}
