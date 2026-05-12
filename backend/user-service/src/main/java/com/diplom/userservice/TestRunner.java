package com.diplom.userservice;

import com.diplom.userservice.event.ProfileChangedEvent;
import com.diplom.userservice.event.EventType;
import com.diplom.userservice.outbox.OutboxEventFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class TestRunner implements CommandLineRunner {

    @Autowired
    private OutboxEventFactory factory;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== TEST RUNNER START ===");
        try {
            ProfileChangedEvent e = new ProfileChangedEvent(UUID.randomUUID(), "test", "test", "test", OffsetDateTime.now());
            factory.create(EventType.PROFILE_CHANGED, e);
            System.out.println("=== TEST RUNNER SUCCESS ===");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
