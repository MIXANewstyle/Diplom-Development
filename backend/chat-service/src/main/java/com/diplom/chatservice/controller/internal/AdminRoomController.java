package com.diplom.chatservice.controller.internal;

import com.diplom.chatservice.dto.admin.AdminRoomInspectionView;
import com.diplom.chatservice.security.SecurityUtils;
import com.diplom.chatservice.service.AdminRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/internal/v1/admin/rooms")
@RequiredArgsConstructor
public class AdminRoomController {

    private final AdminRoomService adminRoomService;

    @GetMapping("/{roomId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminRoomInspectionView getRoomInspectionView(@PathVariable UUID roomId, Authentication authentication) {
        UUID adminUserId = SecurityUtils.getUserIdOrNull(authentication.getPrincipal());
        
        log.info("AUDIT: Admin access - action=INSPECT_ROOM, adminId={}, roomId={}", adminUserId, roomId);
        
        return adminRoomService.getInspectionView(roomId);
    }

    @PostMapping("/{roomId}/terminate")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminRoomInspectionView terminateRoom(@PathVariable UUID roomId, Authentication authentication) {
        UUID adminUserId = SecurityUtils.getUserIdOrNull(authentication.getPrincipal());
        
        log.info("AUDIT: Admin action - action=TERMINATE_ROOM, adminId={}, roomId={}", adminUserId, roomId);
        
        return adminRoomService.terminateRoom(roomId);
    }
}
