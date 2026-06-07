package com.diplom.chatservice.dto.invite;

public record GuestJoinRequest(
        String displayName,
        String gender,
        Integer age
) {}
