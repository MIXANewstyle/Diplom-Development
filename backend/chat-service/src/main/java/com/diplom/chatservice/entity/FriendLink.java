package com.diplom.chatservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "friend_links", schema = "chat_schema")
@IdClass(FriendLinkId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendLink {

    @Id
    @Column(name = "user_a")
    private UUID userA;

    @Id
    @Column(name = "user_b")
    private UUID userB;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
