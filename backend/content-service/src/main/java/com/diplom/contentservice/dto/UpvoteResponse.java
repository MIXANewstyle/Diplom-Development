package com.diplom.contentservice.dto;

public record UpvoteResponse(
    boolean upvoted,
    int upvotesCount
) {}
