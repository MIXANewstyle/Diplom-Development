package com.diplom.contentservice.repository;

import java.util.UUID;

public interface RepliesCountProjection {
    UUID getParentId();
    Long getCnt();
}
