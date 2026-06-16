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
import com.diplom.chatservice.service.RateLimitService;
import com.diplom.chatservice.service.TurnOrchestrationService;
import com.diplom.chatservice.service.TurnPersistenceService;
import com.diplom.chatservice.service.WsErrorSender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
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
    private final com.diplom.chatservice.service.RoomBroadcaster roomBroadcaster;
    private final ThreadPoolTaskExecutor aiExecutor;
    private final WsErrorSender wsErrorSender;
    private final RateLimitService rateLimitService;
    private final MeterRegistry meterRegistry;

    @MessageMapping("/rooms/{roomId}/finish")
    public void finishThought(
            @DestinationVariable UUID roomId,
            @Payload FinishRequest request,
            Principal principal
    ) {
        MDC.put("roomId", roomId.toString());
        try {
            Object authPrincipal = ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        String principalName = "unknown";
        UUID userId = com.diplom.chatservice.security.SecurityUtils.getUserIdOrNull(authPrincipal);
        
        if (authPrincipal instanceof CustomUserDetails user) {
            principalName = user.getUsername();
        } else if (authPrincipal instanceof com.diplom.chatservice.security.GuestPrincipal guest) {
            principalName = "guest-" + guest.getParticipantId();
        }
        
        Long turnSeq = request != null ? request.turnSeq() : null;

        RoomParticipant caller = com.diplom.chatservice.security.SecurityUtils.getParticipantOrNull(authPrincipal, roomId, participantRepository);

        Room room = roomRepository.findById(roomId).orElse(null);

        int headSeq = turnRepository.findByRoomIdOrderBySeqAsc(roomId, Pageable.unpaged())
                .stream()
                .map(Turn::getSeq)
                .max(Integer::compareTo)
                .orElse(0);

        List<DraftBubble> buffer = caller != null
                ? draftService.readBuffer(roomId, caller.getId())
                : List.of();

        boolean isFloorHolder = room != null && caller != null
                && (room.getTypeId() != 1 || caller.getId().equals(room.getCurrentFloorParticipantId()));

        log.info(
                "FINISH_THOUGHT entry roomId={} callerParticipantId={} turnSeq={} bufferSize={} headSeq={} phase={} isFloorHolder={}",
                roomId,
                caller != null ? caller.getId() : null,
                turnSeq,
                buffer.size(),
                headSeq,
                room != null ? room.getPhase() : null,
                isFloorHolder
        );

        if (caller == null) {
            log.info("FINISH_THOUGHT branch=REJECTED roomId={} reason=caller_not_participant", roomId);
            wsErrorSender.send(principalName, WsError.error("Caller is not a participant of this room"));
            return;
        }

        if (room == null || room.getStatusId() != 3) { // 3=ACTIVE
            log.info("FINISH_THOUGHT branch=REJECTED roomId={} reason=room_not_active statusId={}",
                    roomId, room != null ? room.getStatusId() : null);
            wsErrorSender.send(principalName, WsError.error("Room is not ACTIVE"));
            return;
        }

        if (!"A_COMPOSING".equals(room.getPhase())) {
            log.info("FINISH_THOUGHT branch=REJECTED roomId={} reason=wrong_phase phase={}", roomId, room.getPhase());
            wsErrorSender.send(principalName, WsError.error("AI is processing or room not ready"));
            return;
        }

        if (room.getTypeId() == 1 && !caller.getId().equals(room.getCurrentFloorParticipantId())) { // 1=PAIRED
            log.info("FINISH_THOUGHT branch=REJECTED roomId={} reason=not_floor_holder floorHolder={}",
                    roomId, room.getCurrentFloorParticipantId());
            wsErrorSender.send(principalName, WsError.error("It is not your turn to submit"));
            return;
        }

        // Phase 4d Rate Limit Checks
        if (rateLimitService.checkTurnRate(caller.getId())) {
            log.info("FINISH_THOUGHT branch=REJECTED roomId={} reason=rate_limit participantId={}", roomId, caller.getId());
            wsErrorSender.send(principalName, WsError.limit("Slow down"));
            return;
        }

        if (userId != null && rateLimitService.isOverDailyBudget(userId)) {
            log.info("FINISH_THOUGHT branch=REJECTED roomId={} reason=daily_budget userId={}", roomId, userId);
            wsErrorSender.send(principalName, WsError.limit("Daily usage limit reached"));
            return;
        }

        if (!buffer.isEmpty()) {
            long expectedSeq = headSeq + 1L;
            if (turnSeq == null || turnSeq != expectedSeq) {
                log.info("FINISH_THOUGHT branch=IGNORED roomId={} expectedTurnSeq={} gotTurnSeq={}",
                        roomId, expectedSeq, turnSeq);
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (DraftBubble bubble : buffer) {
                sb.append(bubble.text());
            }
            String joinedText = sb.toString();

            if (joinedText.length() > 2000) {
                log.info("FINISH_THOUGHT branch=REJECTED roomId={} reason=text_over_limit length={}",
                        roomId, joinedText.length());
                wsErrorSender.send(principalName, WsError.limit("Packaged thought exceeds 2000 characters limit"));
                return;
            }

            log.info("FINISH_THOUGHT branch=FRESH roomId={}", roomId);

            Turn userTurn = turnPersistenceService.persistUserTurn(roomId, caller.getId(), joinedText);
            log.info("USER turn persisted roomId={} seq={}", roomId, userTurn.getSeq());

            Timer.Sample timer = Timer.start(meterRegistry);

            draftService.clearBuffer(roomId, caller.getId());

            UserTurnDto userTurnDto = new UserTurnDto(
                    userTurn.getId(), userTurn.getSeq(), userTurn.getParticipantId(), userTurn.getContent(), userTurn.getCreatedAt());
            roomBroadcaster.broadcast(roomId, AiThinkingEvent.of(userTurnDto));
            log.info("AI_THINKING broadcast roomId={}", roomId);

            submitAiTask(roomId, room.getTypeId(), timer);
            return;
        }

        // Buffer is empty (Step C)
        List<Turn> allTurns = turnRepository.findByRoomIdOrderBySeqAsc(roomId, Pageable.unpaged()).getContent();
        if (!allTurns.isEmpty()) {
            Turn lastTurn = allTurns.get(allTurns.size() - 1);
            if (lastTurn.getRoleId() == 1 && caller.getId().equals(lastTurn.getParticipantId())) {
                log.info("FINISH_THOUGHT branch=RETRY roomId={} lastUserTurnSeq={}", roomId, lastTurn.getSeq());
                
                Timer.Sample timer = Timer.start(meterRegistry);

                turnPersistenceService.setAiProcessing(roomId);
                roomBroadcaster.broadcast(roomId, AiThinkingEvent.of(null));
                log.info("AI_THINKING broadcast roomId={} (retry)", roomId);
                submitAiTask(roomId, room.getTypeId(), timer);
                return;
            }
        }

        log.info("FINISH_THOUGHT branch=NO_OP roomId={} callerParticipantId={}", roomId, caller.getId());
        } finally {
            MDC.remove("roomId");
        }
    }

    private void submitAiTask(UUID roomId, Integer roomTypeId, Timer.Sample timer) {
        try {
            aiExecutor.execute(() -> {
                MDC.put("roomId", roomId.toString());
                try {
                    log.info("AI task start roomId={}", roomId);
                    try {
                        Turn assistantTurn = turnOrchestrationService.executeAiStep(roomId);
                        log.info("ASSISTANT turn persisted roomId={} seq={}", roomId, assistantTurn.getSeq());

                        AssistantTurnDto assistantTurnDto = new AssistantTurnDto(
                                assistantTurn.getId(), assistantTurn.getSeq(), assistantTurn.getContent(), assistantTurn.getCreatedAt());
                        roomBroadcaster.broadcast(roomId, AiResponseEvent.of(assistantTurnDto));
                        log.info("AI_RESPONSE broadcast roomId={} seq={}", roomId, assistantTurn.getSeq());

                        timer.stop(meterRegistry.timer("chat.turn.roundtrip.latency", "outcome", "success"));

                        if (roomTypeId == 1) { // PAIRED
                            Room updatedRoom = roomRepository.findById(roomId).orElseThrow();
                            roomBroadcaster.broadcast(
                                    roomId,
                                    TurnChangedEvent.of(updatedRoom.getCurrentFloorParticipantId()));
                            log.info("TURN_CHANGED broadcast roomId={} floorHolder={}",
                                    roomId, updatedRoom.getCurrentFloorParticipantId());
                        }
                    } catch (com.diplom.chatservice.exception.LlmRateLimitedException e) {
                        log.warn("AI task failed due to rate limit roomId={}", roomId);
                        turnPersistenceService.handleAiFailure(roomId);
                        timer.stop(meterRegistry.timer("chat.turn.roundtrip.latency", "outcome", "failure"));
                        roomBroadcaster.broadcast(roomId, AiErrorEvent.of(e.getMessage()));
                    } catch (Throwable t) {
                        log.error("AI task failed roomId={}", roomId, t);
                        turnPersistenceService.handleAiFailure(roomId);
                        timer.stop(meterRegistry.timer("chat.turn.roundtrip.latency", "outcome", "failure"));
                        roomBroadcaster.broadcast(
                                roomId,
                                AiErrorEvent.of(t.getMessage() != null ? t.getMessage() : "AI processing failed"));
                    } finally {
                        try {
                            Room room = roomRepository.findById(roomId).orElse(null);
                            if (room != null && "AI_PROCESSING".equals(room.getPhase())) {
                                log.error("room left in AI_PROCESSING — forced reset roomId={}", roomId);
                                turnPersistenceService.handleAiFailure(roomId);
                                roomBroadcaster.broadcast(
                                        roomId,
                                        AiErrorEvent.of("Room left in AI_PROCESSING — forced reset"));
                            }
                        } catch (Throwable cleanupError) {
                            log.error("AI task finally cleanup failed roomId={}", roomId, cleanupError);
                        }
                    }
                } finally {
                    MDC.remove("roomId");
                }
            });
            log.info("AI async task submitted roomId={}", roomId);
        } catch (RejectedExecutionException e) {
            log.warn("AI executor rejected task roomId={}", roomId, e);
            turnPersistenceService.handleAiFailure(roomId);
            timer.stop(meterRegistry.timer("chat.turn.roundtrip.latency", "outcome", "failure"));
            roomBroadcaster.broadcast(roomId, LimitEvent.of("AI busy, retry shortly"));
        }
    }
}
