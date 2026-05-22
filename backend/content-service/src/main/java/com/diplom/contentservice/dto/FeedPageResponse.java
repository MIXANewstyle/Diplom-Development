package com.diplom.contentservice.dto;

import java.util.List;

public record FeedPageResponse(
    List<PostResponse> items,
    String nextCursor
) {}
