package com.diplom.billingservice.dto;

import java.util.List;

public record AdminTransactionPageResponse(
        List<AdminTransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
