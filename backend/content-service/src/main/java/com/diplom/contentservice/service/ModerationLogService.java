package com.diplom.contentservice.service;

import com.diplom.contentservice.entity.ModerationAction;
import com.diplom.contentservice.entity.ModerationLog;
import com.diplom.contentservice.entity.ModerationTargetType;
import com.diplom.contentservice.repository.ModerationLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationLogService {

    private final ModerationLogRepository repository;
    private final ObjectMapper objectMapper;

    public void log(
        UUID actorId,
        ModerationAction action,
        ModerationTargetType targetType,
        UUID targetId,
        Map<String, Object> before,
        Map<String, Object> after
    ) {
        ModerationLog entry = ModerationLog.builder()
            .actorId(actorId)
            .action(action.name())
            .targetType(targetType.name())
            .targetId(targetId)
            .beforeSnapshot(toJsonOrNull(before))
            .afterSnapshot(toJsonOrNull(after))
            .build();
        repository.save(entry);
    }

    private String toJsonOrNull(Map<String, Object> map) {
        if (map == null) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize moderation log snapshot: {}", map, ex);
            return null;
        }
    }
}
