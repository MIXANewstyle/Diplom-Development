package com.diplom.userservice.entity;

public enum FriendshipStatus {
    PENDING(1),
    ACCEPTED(2),
    DECLINED(3),
    BLOCKED(4);

    private final int id;

    FriendshipStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static FriendshipStatus fromId(int id) {
        for (FriendshipStatus status : values()) {
            if (status.id == id) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown friendship status id: " + id);
    }
}
