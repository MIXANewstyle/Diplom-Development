package com.diplom.contentservice.repository;

import java.util.UUID;

public interface PostSearchHit {
    UUID getPostId();
    Double getScore();
}
