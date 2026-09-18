package com.diplom.chatservice.diary.support;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.llm.EmbeddingClient;
import com.diplom.chatservice.service.DiaryDateService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the three things the diary pipeline talks to outside the database:
 * the wall clock, the chat model and the embeddings provider. Everything else
 * (Flyway, JPA, Redis rate limits, RabbitMQ outbox, async summarization) runs for real.
 */
@TestConfiguration
public class DiaryTestBeans {

    @Bean
    public MutableClock testClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    public DiaryDateService testDiaryDateService(MutableClock clock) {
        return new DiaryDateService(clock);
    }

    @Bean
    @Primary
    public FakeLlmClient testLlmClient(ChatLlmProperties properties) {
        return new FakeLlmClient(properties);
    }

    @Bean
    @Primary
    public EmbeddingClient testEmbeddingClient(ChatLlmProperties properties) {
        return new BagOfWordsEmbeddingClient(properties.embeddings().dimensions());
    }
}
