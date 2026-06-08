package com.diplom.billingservice.outbox;

import com.diplom.billingservice.config.RabbitMQConfig;
import com.diplom.billingservice.entity.BillingOutboxEvent;
import com.diplom.billingservice.event.EventType;
import com.diplom.billingservice.repository.BillingOutboxEventRepository;
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

    private final BillingOutboxEventRepository billingOutboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<BillingOutboxEvent> pending =
            billingOutboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING");

        for (BillingOutboxEvent event : pending) {
            try {
                String routingKey = getRoutingKey(event.getEventType());
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, event.getPayload());
                event.setStatus("PROCESSED");
                billingOutboxEventRepository.save(event);
            } catch (Exception ex) {
                log.error("Failed to publish outbox event {}", event.getId(), ex);
                break;
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupProcessedEvents() {
        ZonedDateTime threshold = ZonedDateTime.now().minusHours(24);
        int count = billingOutboxEventRepository.deleteProcessedOlderThan(threshold);
        if (count > 0) {
            log.info("Cleaned up {} processed billing outbox events", count);
        }
    }

    private String getRoutingKey(String eventType) {
        return switch (EventType.valueOf(eventType)) {
            case SUBSCRIPTION_CHANGED -> "billing.subscription-changed";
            case PAYMENT_SUCCEEDED    -> "billing.payment-succeeded";
            case PAYMENT_FAILED       -> "billing.payment-failed";
        };
    }
}
