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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A stable fact the assistant remembers about the diary author (a person, a recurring theme,
 * a goal, a trigger, a value). Always in context; visible and deletable by the author.
 */
@Entity
@Table(name = "diary_memory_facts", schema = "chat_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiaryMemoryFact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "content", nullable = false, length = 300)
    private String content;

    @Column(name = "first_seen_date", nullable = false)
    private LocalDate firstSeenDate;

    @Column(name = "last_confirmed_date", nullable = false)
    private LocalDate lastConfirmedDate;

    @Column(name = "source_room_id")
    private UUID sourceRoomId;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private Integer version;
}
