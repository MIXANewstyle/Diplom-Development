package com.diplom.userservice.repository;

import com.diplom.userservice.entity.AuthorFollow;
import com.diplom.userservice.entity.AuthorFollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthorFollowRepository extends JpaRepository<AuthorFollow, AuthorFollowId> {
}
