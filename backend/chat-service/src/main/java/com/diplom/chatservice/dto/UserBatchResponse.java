package com.diplom.chatservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Cached in Redis as JSON. {@code getDisplayName()} is a derived helper, not a field: it must not be
 * serialized (Jackson would emit it as {@code displayName} and then fail to map it back onto the
 * record), and entries written before this fix must still deserialize.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserBatchResponse(
    UUID id,
    String username,
    String fullName,
    String avatarUrl
) {
    @JsonIgnore
    public String getDisplayName() {
        return (fullName != null && !fullName.isBlank()) ? fullName : username;
    }
}
