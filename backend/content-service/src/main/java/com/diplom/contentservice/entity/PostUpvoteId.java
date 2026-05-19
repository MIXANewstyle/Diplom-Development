package com.diplom.contentservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostUpvoteId implements Serializable {
    private UUID postId;
    private UUID userId;
}
