package com.diplom.chatservice.service;

import com.diplom.chatservice.client.PsychProfileClient;
import com.diplom.chatservice.dto.UserBatchResponse;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Captures context_snapshot for registered participants at dialogue start.
 *
 * <p>Network fetches happen OUTSIDE any transaction; the persist phase is a short tx.
 * Idempotent: never overwrites an existing snapshot.
 *
 * <p>§16.4: NEVER log the about content or the psych_profile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextSnapshotService {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final PsychProfileClient psychProfileClient;
    private final ProfileCacheService profileCacheService;
    private final ObjectMapper objectMapper;

    /**
     * Capture context_snapshot for all registered participants whose snapshot is still null.
     *
     * @param roomId    the room that just became ACTIVE
     * @param callerJwt the JWT to use for profile cache lookups
     */
    public void captureForRoom(UUID roomId, String callerJwt) {
        try {
            Room room = roomRepository.findById(roomId).orElse(null);
            if (room == null) {
                log.warn("captureForRoom: room {} not found", roomId);
                return;
            }

            List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);

            // Filter to registered participants (userId not null) with no existing snapshot
            List<RoomParticipant> needsSnapshot = participants.stream()
                    .filter(p -> p.getUserId() != null)
                    .filter(p -> p.getContextSnapshot() == null)
                    .toList();

            if (needsSnapshot.isEmpty()) {
                log.debug("captureForRoom: room {} — all snapshots already captured", roomId);
                return;
            }

            // --- Network fetches OUTSIDE transaction ---

            // Collect user IDs for batch profile lookup
            List<UUID> userIds = needsSnapshot.stream()
                    .map(RoomParticipant::getUserId)
                    .toList();

            // Fetch display names from the profile cache
            Map<UUID, UserBatchResponse> profiles = profileCacheService.getProfiles(userIds, callerJwt);

            // Fetch about text from the psych_profile endpoint (service-authenticated)
            Map<UUID, String> aboutMap = new LinkedHashMap<>();
            for (UUID uid : userIds) {
                String about = psychProfileClient.getAbout(uid);
                aboutMap.put(uid, about);
            }

            // --- Persist in a short transaction ---
            persistSnapshots(needsSnapshot, profiles, aboutMap, roomId);

        } catch (Exception e) {
            // Graceful: capture failure must not break dialogue startup
            log.error("captureForRoom failed for room {}: {}", roomId, e.getMessage());
        }
    }

    // TODO Phase 4e: guests get a snapshot at guest join (§7.2) — displayName/gender/age only

    @Transactional
    protected void persistSnapshots(
            List<RoomParticipant> participants,
            Map<UUID, UserBatchResponse> profiles,
            Map<UUID, String> aboutMap,
            UUID roomId
    ) {
        for (RoomParticipant p : participants) {
            // Double-check idempotency inside the tx
            if (p.getContextSnapshot() != null) {
                continue;
            }

            UUID userId = p.getUserId();
            UserBatchResponse profile = profiles.get(userId);
            String displayName = profile != null ? profile.username() : null;
            String about = aboutMap.get(userId);

            try {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("displayName", displayName);
                snapshot.put("about", about);
                String json = objectMapper.writeValueAsString(snapshot);

                p.setContextSnapshot(json);
                participantRepository.save(p);

                // §16.4: log metadata only, never the about content
                log.info("room {} context: participant {} about={} (len={}), displayName={}",
                        roomId, userId,
                        about != null ? "present" : "absent",
                        about != null ? about.length() : 0,
                        displayName != null ? "present" : "absent");
            } catch (Exception e) {
                log.error("Failed to persist context_snapshot for participant {} in room {}: {}",
                        p.getId(), roomId, e.getMessage());
            }
        }
    }
}
