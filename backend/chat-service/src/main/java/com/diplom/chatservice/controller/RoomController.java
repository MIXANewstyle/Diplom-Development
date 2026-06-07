package com.diplom.chatservice.controller;

import com.diplom.chatservice.dto.CreatePairedRoomRequest;
import com.diplom.chatservice.dto.CreateSoloRoomRequest;
import com.diplom.chatservice.dto.EndRespondRequest;
import com.diplom.chatservice.dto.ParticipantResponse;
import com.diplom.chatservice.dto.RoomResponse;
import com.diplom.chatservice.dto.RoomSummaryResponse;
import com.diplom.chatservice.dto.TurnsPageResponse;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.security.CustomUserDetails;
import com.diplom.chatservice.service.ParticipantEnrichmentService;
import com.diplom.chatservice.service.ContextSnapshotService;
import com.diplom.chatservice.service.ProfileCacheService;
import com.diplom.chatservice.service.RoomMapper;
import com.diplom.chatservice.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import com.diplom.chatservice.dto.SubmitTurnRequest;
import com.diplom.chatservice.dto.SubmitTurnResponse;
import com.diplom.chatservice.service.TurnOrchestrationService;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final TurnOrchestrationService turnOrchestrationService;
    private final ParticipantEnrichmentService participantEnrichmentService;
    private final ProfileCacheService profileCacheService;
    private final ContextSnapshotService contextSnapshotService;
    private final RoomParticipantRepository roomParticipantRepository;
    private final RoomMapper roomMapper;

    @PostMapping("/paired")
    @PreAuthorize("hasRole('BASIC')")
    public ResponseEntity<RoomResponse> createPairedRoom(
        @AuthenticationPrincipal CustomUserDetails user,
        @Valid @RequestBody CreatePairedRoomRequest request
    ) {
        RoomResponse response = roomService.createPairedRoom(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/solo")
    @PreAuthorize("hasRole('BASIC')")
    public ResponseEntity<RoomResponse> createSoloRoom(
        @AuthenticationPrincipal CustomUserDetails user,
        @Valid @RequestBody CreateSoloRoomRequest request,
        HttpServletRequest httpRequest
    ) {
        RoomResponse response = roomService.createSoloRoom(request, user.getId());
        // Solo room is ACTIVE immediately — capture context snapshot outside the tx
        contextSnapshotService.captureForRoom(response.id(), extractJwt(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<RoomResponse> joinRoom(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID roomId
    ) {
        RoomResponse response = roomService.joinRoom(roomId, user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/consent/start")
    public ResponseEntity<RoomResponse> consentStart(
        @AuthenticationPrincipal Object principal,
        @PathVariable UUID roomId,
        HttpServletRequest httpRequest
    ) {
        RoomResponse response = roomService.consentStart(roomId, principal);
        // If this consent caused WAITING_CONSENT → ACTIVE, capture context snapshots
        if ("ACTIVE".equals(response.status())) {
            contextSnapshotService.captureForRoom(roomId, extractJwt(httpRequest));
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/consent/revoke")
    public ResponseEntity<RoomResponse> consentRevoke(
        @AuthenticationPrincipal Object principal,
        @PathVariable UUID roomId
    ) {
        RoomResponse response = roomService.consentRevoke(roomId, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/end/propose")
    public ResponseEntity<RoomResponse> endPropose(
        @AuthenticationPrincipal Object principal,
        @PathVariable UUID roomId
    ) {
        RoomResponse response = roomService.endPropose(roomId, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/end/respond")
    public ResponseEntity<RoomResponse> endRespond(
        @AuthenticationPrincipal Object principal,
        @PathVariable UUID roomId,
        @Valid @RequestBody EndRespondRequest request
    ) {
        RoomResponse response = roomService.endRespond(roomId, request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/end")
    public ResponseEntity<RoomResponse> endSolo(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID roomId
    ) {
        RoomResponse response = roomService.endSolo(roomId, user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<RoomSummaryResponse>> listRooms(
        @AuthenticationPrincipal CustomUserDetails user,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest httpRequest
    ) {
        String jwt = extractJwt(httpRequest);
        List<RoomSummaryResponse> response = roomService.listRoomsEnriched(
            user.getId(), page, size, jwt, profileCacheService, roomMapper);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/archived")
    public ResponseEntity<List<RoomSummaryResponse>> listArchivedRooms(
        @AuthenticationPrincipal CustomUserDetails user,
        @RequestParam(defaultValue = "false") boolean seedEligible,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest httpRequest
    ) {
        String jwt = extractJwt(httpRequest);
        List<RoomSummaryResponse> response;
        if (seedEligible) {
            response = roomService.listSeedEligibleRooms(
                user.getId(), page, size, jwt, profileCacheService, roomMapper);
        } else {
            // Future-proofing: if they want just archived rooms without seedEligible, we can fall back to a different method.
            // For now, if not seedEligible, return empty list or support it if requested. The spec focuses on seedEligible=true.
            response = java.util.Collections.emptyList();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(
        @AuthenticationPrincipal Object principal,
        @PathVariable UUID roomId,
        HttpServletRequest httpRequest
    ) {
        RoomResponse base = roomService.getRoom(roomId, principal);
        String jwt = extractJwt(httpRequest);

        // Enrich the participant list with display names
        List<RoomParticipant> participants = roomParticipantRepository.findByRoomId(roomId);
        List<ParticipantResponse> enriched = participantEnrichmentService.enrichParticipants(participants, jwt);

        RoomResponse enrichedResponse = new RoomResponse(
            base.id(), base.type(), base.status(), base.phase(),
            base.currentFloorParticipantId(), base.aiModel(), base.ownerUserId(),
            base.createdAt(), base.startedAt(), enriched
        );
        return ResponseEntity.ok(enrichedResponse);
    }

    @GetMapping("/{roomId}/turns")
    public ResponseEntity<TurnsPageResponse> getTurns(
        @AuthenticationPrincipal Object principal,
        @PathVariable UUID roomId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        TurnsPageResponse response = roomService.getTurns(roomId, principal, page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/turns")
    public ResponseEntity<SubmitTurnResponse> submitTurn(
        @AuthenticationPrincipal Object principal,
        @PathVariable UUID roomId,
        @Valid @RequestBody SubmitTurnRequest request
    ) {
        SubmitTurnResponse response = turnOrchestrationService.submitTurn(roomId, principal, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/turns/retry")
    public ResponseEntity<SubmitTurnResponse> retryTurn(
        @AuthenticationPrincipal Object principal,
        @PathVariable UUID roomId
    ) {
        SubmitTurnResponse response = turnOrchestrationService.retryTurn(roomId, principal);
        return ResponseEntity.ok(response);
    }

    private String extractJwt(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        throw new IllegalStateException("No bearer token in current request");
    }
}

