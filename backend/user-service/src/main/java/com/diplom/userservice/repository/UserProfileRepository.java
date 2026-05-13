package com.diplom.userservice.repository;

import com.diplom.userservice.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    List<UserProfile> findAllByUserIdIn(Collection<UUID> userIds);
}
