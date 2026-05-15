package com.diplom.contentservice.outbox;

import com.diplom.contentservice.config.RabbitMQConfig;
import com.diplom.contentservice.entity.ContentOutboxEvent;
import com.diplom.contentservice.repository.ContentOutboxEventRepository;
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
    private final ContentOutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<ContentOutboxEvent> pending = outboxEventRepository
            .findTop100ByStatusOrderByCreatedAtAsc("PENDING");
        for (ContentOutboxEvent event : pending) {
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
            case "POST_PUBLISHED"    -> "post.published";
            case "POST_ARCHIVED"     -> "post.archived";
            case "POST_MODERATED"    -> "post.moderated";
            case "COMMENT_CREATED"   -> "comment.created";
            case "COMMENT_MODERATED" -> "comment.moderated";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
