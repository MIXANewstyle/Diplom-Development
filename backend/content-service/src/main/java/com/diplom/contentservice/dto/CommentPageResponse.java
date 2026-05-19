package com.diplom.contentservice.dto;

import java.util.List;

public record CommentPageResponse(
    List<CommentResponse> items,
    String nextCursor
) {}
