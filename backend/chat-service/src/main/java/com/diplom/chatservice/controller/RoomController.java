package com.diplom.chatservice.controller;

import com.diplom.chatservice.dto.CreatePairedRoomRequest;
import com.diplom.chatservice.dto.CreateSoloRoomRequest;
import com.diplom.chatservice.dto.RoomResponse;
import com.diplom.chatservice.dto.RoomSummaryResponse;
import com.diplom.chatservice.dto.TurnsPageResponse;
import com.diplom.chatservice.security.CustomUserDetails;
import com.diplom.chatservice.service.RoomService;
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

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

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
        @Valid @RequestBody CreateSoloRoomRequest request
    ) {
        RoomResponse response = roomService.createSoloRoom(request, user.getId());
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
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID roomId
    ) {
        RoomResponse response = roomService.consentStart(roomId, user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<RoomSummaryResponse>> listRooms(
        @AuthenticationPrincipal CustomUserDetails user,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<RoomSummaryResponse> response = roomService.listRooms(user.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID roomId
    ) {
        RoomResponse response = roomService.getRoom(roomId, user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roomId}/turns")
    public ResponseEntity<TurnsPageResponse> getTurns(
        @AuthenticationPrincipal CustomUserDetails user,
        @PathVariable UUID roomId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        TurnsPageResponse response = roomService.getTurns(roomId, user.getId(), page, size);
        return ResponseEntity.ok(response);
    }
}
