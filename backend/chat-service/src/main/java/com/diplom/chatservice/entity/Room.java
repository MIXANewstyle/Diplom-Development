package com.diplom.chatservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rooms", schema = "chat_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "type_id", nullable = false)
    private Integer typeId;

    @Column(name = "solo_mode_id")
    private Integer soloModeId;

    @Column(name = "status_id", nullable = false)
    private Integer statusId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "current_floor_participant_id")
    private UUID currentFloorParticipantId;

    @Column(name = "ending_proposed_by_participant_id")
    private UUID endingProposedByParticipantId;

    @Column(name = "phase", length = 20)
    private String phase;

    @Column(name = "ai_model", nullable = false, length = 100)
    private String aiModel;

    @Column(name = "seed_context_room_id")
    private UUID seedContextRoomId;

    @Column(name = "running_summary", columnDefinition = "TEXT")
    private String runningSummary;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Version
    @Column(nullable = false)
    private Integer version;
}
