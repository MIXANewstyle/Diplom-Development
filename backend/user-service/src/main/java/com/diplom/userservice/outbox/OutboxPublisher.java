package com.diplom.userservice.outbox;

import com.diplom.userservice.config.RabbitMQConfig;
import com.diplom.userservice.entity.UserOutboxEvent;
import com.diplom.userservice.event.EventType;
import com.diplom.userservice.repository.UserOutboxEventRepository;
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

    private final UserOutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<UserOutboxEvent> pendingEvents = outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING");

        for (UserOutboxEvent event : pendingEvents) {
            try {
                String routingKey = getRoutingKey(event.getEventType());
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, event.getPayload());

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
        int deletedCount = outboxEventRepository.deleteProcessedOlderThan(threshold);
        if (deletedCount > 0) {
            log.info("Cleaned up {} processed outbox events", deletedCount);
        }
    }

    private String getRoutingKey(String eventType) {
        return switch (EventType.valueOf(eventType)) {
            case USER_REGISTERED -> "user.registered";
            case PROFILE_CHANGED -> "user.profile-changed";
            case ROLE_UPDATED -> "user.role-updated";
            case ACCOUNT_MODERATED -> "user.account-moderated";
        };
    }
}
