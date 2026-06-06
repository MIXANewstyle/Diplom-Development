package com.diplom.chatservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "room_participants", schema = "chat_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @Column(name = "guest_display_name", length = 100)
    private String guestDisplayName;

    @Column(name = "guest_gender_id")
    private Integer guestGenderId;

    @Column(name = "guest_age")
    private Integer guestAge;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_snapshot", columnDefinition = "jsonb")
    private String contextSnapshot;

    @Column(name = "consent_start_at")
    private OffsetDateTime consentStartAt;

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;
}
