package com.diplom.chatservice.service;

import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Background sweeps for ABANDONED and EXPIRED rooms (§2.2).
 *
 * <p>Abandonment candidates are ENDING rooms only — ACTIVE dialogues are not
 * auto-abandoned after {@code abandonment-timeout}, so brief absence / tab-hide
 * does not destroy an in-progress conversation (guest recovery remains possible
 * until the guest JWT expires or the room is ended).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomSweepService {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final PresenceService presenceService;
    private final RoomService roomService;

    @Value("${chat.sweeps.abandonment-timeout}")
    private Duration abandonmentTimeout;

    @Value("${chat.sweeps.creation-ttl}")
    private Duration creationTtl;

    @Scheduled(fixedDelayString = "${chat.sweeps.interval}")
    public void sweepAbandonedRooms() {
        log.debug("Starting sweep for abandoned rooms");
        int page = 0;
        int size = 50;
        Page<Room> candidates;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = now.minus(abandonmentTimeout);

        do {
            candidates = roomRepository.findAbandonedCandidates(PageRequest.of(page, size));
            for (Room room : candidates.getContent()) {
                try {
                    checkAndAbandonRoom(room, threshold);
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.debug("Room {} modified concurrently, skipping in sweep", room.getId());
                } catch (Exception e) {
                    log.warn("Error processing room {} in abandoned sweep", room.getId(), e);
                }
            }
            page++;
        } while (candidates.hasNext());
    }

    private void checkAndAbandonRoom(Room room, OffsetDateTime threshold) {
        List<RoomParticipant> participants = roomParticipantRepository.findByRoomId(room.getId());
        Set<UUID> onlineIds = presenceService.getOnlineParticipants(room.getId());
        boolean anyAbandoned = false;

        for (RoomParticipant p : participants) {
            UUID participantId = p.getId();
            
            // Check Redis presence
            boolean isOnline = onlineIds.contains(participantId);
            if (isOnline) {
                continue;
            }

            // Check lastSeenAt fallback to joinedAt
            OffsetDateTime seen = p.getLastSeenAt() != null ? p.getLastSeenAt() : p.getJoinedAt();
            
            // If joinedAt is null (participant invited but hasn't joined), 
            // they can't be "abandoned" based on presence if they never joined. 
            // But if there's an initiator who joined and left, the room can be abandoned.
            if (seen != null && seen.isBefore(threshold)) {
                anyAbandoned = true;
                break;
            }
        }

        if (anyAbandoned) {
            roomService.abandonRoom(room.getId());
            log.info("Sweeper transitioned room {} to ABANDONED", room.getId());
        }
    }

    @Scheduled(fixedDelayString = "${chat.sweeps.interval}")
    public void sweepExpiredRooms() {
        log.debug("Starting sweep for expired rooms");
        int page = 0;
        int size = 50;
        Page<Room> candidates;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = now.minus(creationTtl);

        do {
            candidates = roomRepository.findExpiredCandidates(threshold, PageRequest.of(page, size));
            for (Room room : candidates.getContent()) {
                try {
                    roomService.expireRoom(room.getId());
                    log.info("Sweeper transitioned room {} to EXPIRED", room.getId());
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.debug("Room {} modified concurrently, skipping in sweep", room.getId());
                } catch (Exception e) {
                    log.warn("Error processing room {} in expired sweep", room.getId(), e);
                }
            }
            page++;
        } while (candidates.hasNext());
    }
}
