package com.diplom.chatservice.controller.ws;

import com.diplom.chatservice.dto.ws.*;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import com.diplom.chatservice.security.CustomUserDetails;
import com.diplom.chatservice.service.DraftService;
import com.diplom.chatservice.service.TurnOrchestrationService;
import com.diplom.chatservice.service.TurnPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TurnWsController {

    private final RoomRepository roomRepository;
    private final TurnRepository turnRepository;
    private final RoomParticipantRepository participantRepository;
    private final DraftService draftService;
    private final TurnPersistenceService turnPersistenceService;
    private final TurnOrchestrationService turnOrchestrationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ThreadPoolTaskExecutor aiExecutor;

    @MessageMapping("/rooms/{roomId}/finish")
    public void finishThought(
            @AuthenticationPrincipal CustomUserDetails user,
            @DestinationVariable UUID roomId,
            @Payload FinishThoughtRequest request
    ) {
        RoomParticipant caller = participantRepository.findByRoomIdAndUserId(roomId, user.getId())
                .orElse(null);

        if (caller == null) {
            sendError(user.getUsername(), "Caller is not a participant of this room");
            return;
        }

        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null || room.getStatusId() != 3) { // 3=ACTIVE
            sendError(user.getUsername(), "Room is not ACTIVE");
            return;
        }

        if (!"A_COMPOSING".equals(room.getPhase())) {
            sendError(user.getUsername(), "AI is processing or room not ready");
            return;
        }

        if (room.getTypeId() == 1 && !caller.getId().equals(room.getCurrentFloorParticipantId())) { // 1=PAIRED
            sendError(user.getUsername(), "It is not your turn to submit");
            return;
        }

        // Step B & C
        int headSeq = turnRepository.findByRoomIdOrderBySeqAsc(roomId, Pageable.unpaged())
                .stream()
                .map(Turn::getSeq)
                .max(Integer::compareTo)
                .orElse(0);

        List<DraftBubble> buffer = draftService.readBuffer(roomId, caller.getId());

        if (!buffer.isEmpty()) {
            int expectedSeq = headSeq + 1;
            if (request.turnSeq() != expectedSeq) {
                log.debug("Ignored finish_thought due to stale turnSeq. expected={}, got={}", expectedSeq, request.turnSeq());
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (DraftBubble bubble : buffer) {
                sb.append(bubble.text());
            }
            String joinedText = sb.toString();

            if (joinedText.length() > 8000) {
                sendLimitError(user.getUsername(), "Packaged thought exceeds 8000 characters limit");
                return;
            }

            Turn userTurn = turnPersistenceService.persistUserTurn(roomId, caller.getId(), joinedText);
            draftService.clearBuffer(roomId, caller.getId());

            UserTurnDto userTurnDto = new UserTurnDto(userTurn.getId(), userTurn.getSeq(), userTurn.getParticipantId(), userTurn.getContent());
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId, AiThinkingEvent.of(userTurnDto));
            submitAiTask(roomId, room.getTypeId());
            return;
        }

        // Buffer is empty (Step C)
        List<Turn> allTurns = turnRepository.findByRoomIdOrderBySeqAsc(roomId, Pageable.unpaged()).getContent();
        if (!allTurns.isEmpty()) {
            Turn lastTurn = allTurns.get(allTurns.size() - 1);
            if (lastTurn.getRoleId() == 1 && caller.getId().equals(lastTurn.getParticipantId())) {
                // Retry
                turnPersistenceService.setAiProcessing(roomId);
                messagingTemplate.convertAndSend("/topic/rooms/" + roomId, AiThinkingEvent.of(null));
                submitAiTask(roomId, room.getTypeId());
                return;
            }
        }

        log.debug("Ignored empty finish_thought from {}", caller.getId());
    }

    private void sendError(String username, String message) {
        messagingTemplate.convertAndSendToUser(username, "/queue/errors", WsError.error(message));
    }

    private void sendLimitError(String username, String message) {
        messagingTemplate.convertAndSendToUser(username, "/queue/errors", WsError.limit(message));
    }

    private void submitAiTask(UUID roomId, Integer roomTypeId) {
        try {
            aiExecutor.execute(() -> {
                try {
                    Turn assistantTurn = turnOrchestrationService.executeAiStep(roomId);
                    AssistantTurnDto assistantTurnDto = new AssistantTurnDto(assistantTurn.getId(), assistantTurn.getSeq(), assistantTurn.getContent());
                    messagingTemplate.convertAndSend("/topic/rooms/" + roomId, AiResponseEvent.of(assistantTurnDto));
                    
                    if (roomTypeId == 1) { // PAIRED
                        Room updatedRoom = roomRepository.findById(roomId).orElseThrow();
                        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, TurnChangedEvent.of(updatedRoom.getCurrentFloorParticipantId()));
                    }
                } catch (Exception e) {
                    log.error("AI step failed for room {}", roomId, e);
                    turnPersistenceService.handleAiFailure(roomId);
                    messagingTemplate.convertAndSend("/topic/rooms/" + roomId, AiErrorEvent.of(e.getMessage() != null ? e.getMessage() : "AI processing failed"));
                }
            });
        } catch (RejectedExecutionException e) {
            turnPersistenceService.handleAiFailure(roomId);
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId, LimitEvent.of("AI busy, retry shortly"));
        }
    }
}
