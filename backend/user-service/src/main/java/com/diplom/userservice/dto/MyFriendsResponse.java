package com.diplom.userservice.dto;

import java.util.List;

public record MyFriendsResponse(
    List<UserBatchResponse> friends,
    List<UserBatchResponse> incomingRequests,
    List<UserBatchResponse> outgoingRequests
) {}
