package com.diplom.chatservice.service;

import com.diplom.chatservice.repository.ChatOutboxEventRepository;
import com.diplom.chatservice.repository.RoomRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMetricsService {

    private final MeterRegistry meterRegistry;
    private final RoomRepository roomRepository;
    private final ChatOutboxEventRepository outboxEventRepository;
    private final WsSessionRegistry wsSessionRegistry;

    private final AtomicInteger activeRooms = new AtomicInteger(0);
    private final AtomicInteger pendingOutbox = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        Gauge.builder("chat.rooms.active", activeRooms, AtomicInteger::get)
             .description("Number of ACTIVE rooms")
             .register(meterRegistry);

        Gauge.builder("chat.ws.connections", wsSessionRegistry, WsSessionRegistry::getConnectionCount)
             .description("Current live WS sessions")
             .register(meterRegistry);

        Gauge.builder("chat.outbox.pending", pendingOutbox, AtomicInteger::get)
             .description("Count of PENDING outbox events")
             .register(meterRegistry);
    }

    @Scheduled(fixedRate = 30000)
    public void refreshMetrics() {
        try {
            int active = roomRepository.countByStatusId(3); // 3 = ACTIVE
            activeRooms.set(active);

            int pending = outboxEventRepository.countByStatus("PENDING");
            pendingOutbox.set(pending);

            log.debug("Metrics refreshed: activeRooms={}, pendingOutbox={}", active, pending);
        } catch (Exception e) {
            log.error("Failed to refresh metrics", e);
        }
    }
}
