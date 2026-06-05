package com.diplom.chatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.diplom.chatservice.config.ChatLlmProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ChatLlmProperties.class)
public class ChatServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}
