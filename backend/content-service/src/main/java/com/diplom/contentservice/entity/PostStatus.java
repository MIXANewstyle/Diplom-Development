package com.diplom.contentservice.entity;

public enum PostStatus {
    DRAFT(1),
    PUBLISHED(2),
    ARCHIVED(3),
    MODERATED(4);

    private final int id;

    PostStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static PostStatus fromId(int id) {
        for (PostStatus status : values()) {
            if (status.getId() == id) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown PostStatus id: " + id);
    }
}
