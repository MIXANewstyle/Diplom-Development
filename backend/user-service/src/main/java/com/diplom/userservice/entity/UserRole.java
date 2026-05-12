package com.diplom.userservice.entity;

public enum UserRole {
    GUEST(1, "GUEST"),
    FREE(2, "FREE"),
    BASIC(3, "BASIC"),
    AUTHOR(4, "AUTHOR"),
    ADMIN(5, "ADMIN");

    private final int id;
    private final String name;

    UserRole(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static UserRole fromId(int id) {
        for (UserRole r : values()) {
            if (r.id == id) return r;
        }
        throw new IllegalArgumentException("Unknown role id: " + id);
    }
}
