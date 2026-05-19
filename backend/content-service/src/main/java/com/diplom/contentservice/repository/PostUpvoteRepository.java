package com.diplom.contentservice.repository;

import com.diplom.contentservice.entity.PostUpvote;
import com.diplom.contentservice.entity.PostUpvoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostUpvoteRepository extends JpaRepository<PostUpvote, PostUpvoteId> {
}
