package com.diplom.chatservice.service;

import com.diplom.chatservice.dto.ParticipantResponse;
import com.diplom.chatservice.dto.RoomResponse;
import com.diplom.chatservice.dto.RoomSummaryResponse;
import com.diplom.chatservice.dto.TurnResponse;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoomMapper {

    // Dictionary id → name mappings (match V1__init_chat_schema.sql)
    public String roomTypeName(Integer typeId) {
        return switch (typeId) {
            case 1 -> "PAIRED";
            case 2 -> "SOLO";
            default -> "UNKNOWN";
        };
    }

    public String roomStatusName(Integer statusId) {
        return switch (statusId) {
            case 1 -> "CREATED";
            case 2 -> "WAITING_CONSENT";
            case 3 -> "ACTIVE";
            case 4 -> "ENDING";
            case 5 -> "ARCHIVED";
            case 6 -> "ABANDONED";
            case 7 -> "EXPIRED";
            default -> "UNKNOWN";
        };
    }

    public String participantRoleName(Integer roleId) {
        return switch (roleId) {
            case 1 -> "INITIATOR";
            case 2 -> "INVITEE";
            case 3 -> "SOLO";
            default -> "UNKNOWN";
        };
    }

    public String turnRoleName(Integer roleId) {
        return switch (roleId) {
            case 1 -> "USER";
            case 2 -> "ASSISTANT";
            case 3 -> "SYSTEM";
            default -> "UNKNOWN";
        };
    }

    public RoomResponse toRoomResponse(Room room, List<RoomParticipant> participants) {
        List<ParticipantResponse> participantResponses = participants.stream()
            .map(this::toParticipantResponse)
            .toList();

        return new RoomResponse(
            room.getId(),
            room.getTitle(),
            roomTypeName(room.getTypeId()),
            roomStatusName(room.getStatusId()),
            room.getPhase(),
            room.getCurrentFloorParticipantId(),
            room.getAiModel(),
            room.getOwnerUserId(),
            room.getCreatedAt(),
            room.getStartedAt(),
            participantResponses
        );
    }

    public RoomSummaryResponse toRoomSummaryResponse(Room room, RoomParticipant myParticipant,
                                                      String otherDisplayName, String otherAvatarUrl) {
        return new RoomSummaryResponse(
            room.getId(),
            room.getTitle(),
            roomTypeName(room.getTypeId()),
            roomStatusName(room.getStatusId()),
            participantRoleName(myParticipant.getRoleId()),
            room.getCreatedAt(),
            room.getStartedAt(),
            otherDisplayName,
            otherAvatarUrl
        );
    }

    public ParticipantResponse toParticipantResponse(RoomParticipant p) {
        return new ParticipantResponse(
            p.getId(),
            p.getUserId(),
            participantRoleName(p.getRoleId()),
            p.getConsentStartAt(),
            p.getJoinedAt(),
            p.getGuestDisplayName(),
            p.getGuestGenderId(),
            p.getGuestAge(),
            null,  // displayName — populated by ParticipantEnrichmentService
            null   // avatarUrl — populated by ParticipantEnrichmentService
        );
    }

    public TurnResponse toTurnResponse(Turn t) {
        return new TurnResponse(
            t.getId(),
            t.getRoomId(),
            t.getSeq(),
            turnRoleName(t.getRoleId()),
            t.getParticipantId(),
            t.getContent(),
            t.getPromptTokens(),
            t.getCompletionTokens(),
            t.getCreatedAt()
        );
    }
}
