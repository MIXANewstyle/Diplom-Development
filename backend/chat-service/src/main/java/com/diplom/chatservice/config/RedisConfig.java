package com.diplom.chatservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        GenericJackson2JsonRedisSerializer valueSerializer =
            new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            com.diplom.chatservice.service.RoomEventsRelayListener roomEventsRelayListener,
            com.diplom.chatservice.service.SessionTerminationListener sessionTerminationListener
    ) {
        org.springframework.data.redis.listener.RedisMessageListenerContainer container =
                new org.springframework.data.redis.listener.RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // PatternTopic for room.events.*
        container.addMessageListener(
                new org.springframework.data.redis.listener.adapter.MessageListenerAdapter(roomEventsRelayListener),
                new org.springframework.data.redis.listener.PatternTopic("room.events.*")
        );

        // ChannelTopic for chat:control:terminate-user
        container.addMessageListener(
                new org.springframework.data.redis.listener.adapter.MessageListenerAdapter(sessionTerminationListener),
                new org.springframework.data.redis.listener.ChannelTopic("chat:control:terminate-user")
        );

        return container;
    }
}
