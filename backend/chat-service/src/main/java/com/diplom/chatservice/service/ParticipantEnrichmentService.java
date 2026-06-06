package com.diplom.chatservice.service;

import com.diplom.chatservice.dto.ParticipantResponse;
import com.diplom.chatservice.dto.UserBatchResponse;
import com.diplom.chatservice.entity.RoomParticipant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Enriches participant lists with display names and avatars from user-service.
 *
 * <p>Registered participants (userId != null) are resolved via {@link ProfileCacheService}.
 * Guest participants keep their local {@code guestDisplayName}; avatar is null.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantEnrichmentService {

    private final ProfileCacheService profileCacheService;
    private final RoomMapper roomMapper;

    /**
     * Map participants to enriched {@link ParticipantResponse} records with display names
     * and avatars resolved from user-service.
     *
     * @param participants the raw participant entities
     * @param jwt          the raw JWT to forward to user-service
     * @return enriched participant response list
     */
    public List<ParticipantResponse> enrichParticipants(List<RoomParticipant> participants, String jwt) {
        // Collect registered user IDs (skip guests)
        List<UUID> registeredUserIds = participants.stream()
            .map(RoomParticipant::getUserId)
            .filter(Objects::nonNull)
            .toList();

        // Batch-fetch profiles via cache-aside
        Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(registeredUserIds, jwt);

        return participants.stream()
            .map(p -> {
                ParticipantResponse base = roomMapper.toParticipantResponse(p);

                if (p.getUserId() != null) {
                    // Registered participant — enrich from profile lookup
                    UserBatchResponse profile = profiles.get(p.getUserId());
                    if (profile != null) {
                        return new ParticipantResponse(
                            base.id(),
                            base.userId(),
                            base.role(),
                            base.consentStartAt(),
                            base.joinedAt(),
                            base.guestDisplayName(),
                            base.guestGenderId(),
                            base.guestAge(),
                            profile.username(),  // displayName
                            profile.avatarUrl()   // avatarUrl
                        );
                    }
                }
                // Guest participant or profile not found — no enrichment
                return base;
            })
            .toList();
    }
}
