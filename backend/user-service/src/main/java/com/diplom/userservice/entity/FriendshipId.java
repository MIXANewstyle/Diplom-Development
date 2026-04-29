package com.diplom.userservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendshipId implements Serializable {
    private UUID requesterId;
    private UUID addresseeId;
}
