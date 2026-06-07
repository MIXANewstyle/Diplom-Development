package com.diplom.chatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.diplom.chatservice.config.ChatLlmProperties;

import com.diplom.chatservice.config.ChatInviteProperties;
import com.diplom.chatservice.config.ChatGuestProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    ChatLlmProperties.class, 
    ChatLimitsProperties.class,
    ChatInviteProperties.class,
    ChatGuestProperties.class
})
public class ChatServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}
