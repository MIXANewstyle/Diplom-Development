package com.diplom.chatservice.controller;

import com.diplom.chatservice.dto.invite.GuestJoinRequest;
import com.diplom.chatservice.dto.invite.InviteLandingResponse;
import com.diplom.chatservice.dto.invite.JoinInviteResponse;
import com.diplom.chatservice.dto.invite.MintInviteResponse;
import com.diplom.chatservice.security.CustomUserDetails;
import com.diplom.chatservice.service.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invites")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @PostMapping("/mint")
    public ResponseEntity<MintInviteResponse> mintInvite(
            @RequestParam UUID roomId,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        MintInviteResponse response = inviteService.mintInvite(roomId, user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revokeInvite(
            @RequestParam UUID roomId,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        inviteService.revokeInvite(roomId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{token}/landing")
    public ResponseEntity<InviteLandingResponse> getLandingInfo(@PathVariable String token) {
        InviteLandingResponse response = inviteService.getLandingInfo(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{token}/join")
    public ResponseEntity<Void> joinRegistered(
            @PathVariable String token,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        inviteService.joinRegistered(token, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{token}/join/guest")
    public ResponseEntity<JoinInviteResponse> joinGuest(
            @PathVariable String token,
            @RequestBody GuestJoinRequest request
    ) {
        JoinInviteResponse response = inviteService.joinGuest(token, request);
        return ResponseEntity.ok(response);
    }
}
