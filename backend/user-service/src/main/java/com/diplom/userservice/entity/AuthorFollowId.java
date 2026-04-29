package com.diplom.userservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthorFollowId implements Serializable {
    private UUID followerId;
    private UUID authorId;
}
