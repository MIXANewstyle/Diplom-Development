package com.diplom.userservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "user.events.exchange";
    public static final String BILLING_EVENTS_EXCHANGE = "billing.events.exchange";
    public static final String BILLING_EVENTS_QUEUE = "user-service.billing-events.queue";

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public TopicExchange billingEventsExchange() {
        return new TopicExchange(BILLING_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue billingEventsQueue() {
        return new Queue(BILLING_EVENTS_QUEUE, true);  // durable
    }

    @Bean
    public Binding subscriptionChangedBinding(Queue billingEventsQueue, TopicExchange billingEventsExchange) {
        return BindingBuilder.bind(billingEventsQueue).to(billingEventsExchange).with("billing.subscription-changed");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
