package com.diplom.chatservice.dto;

import java.util.List;

public record TurnsPageResponse(
    List<TurnResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
