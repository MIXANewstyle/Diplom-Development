package com.diplom.chatservice.outbox;

import com.diplom.chatservice.config.RabbitMQConfig;
import com.diplom.chatservice.entity.ChatOutboxEvent;
import com.diplom.chatservice.repository.ChatOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {
    private final ChatOutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<ChatOutboxEvent> pending = outboxEventRepository
            .findTop100ByStatusOrderByCreatedAtAsc("PENDING");
        for (ChatOutboxEvent event : pending) {
            try {
                String routingKey = getRoutingKey(event.getEventType());
                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME, routingKey, event.getPayload());
                event.setStatus("PROCESSED");
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                log.error("Failed to publish event {}", event.getId(), ex);
                break;
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupProcessedEvents() {
        ZonedDateTime threshold = ZonedDateTime.now().minusHours(24);
        int deleted = outboxEventRepository.deleteProcessedOlderThan(threshold);
        if (deleted > 0) {
            log.info("Cleaned up {} processed outbox events", deleted);
        }
    }

    private String getRoutingKey(String eventType) {
        return switch (eventType) {
            case "PAIR_INVITE_SENT" -> "chat.invite-sent";
            case "ROOM_ARCHIVED"    -> "chat.room-archived";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
