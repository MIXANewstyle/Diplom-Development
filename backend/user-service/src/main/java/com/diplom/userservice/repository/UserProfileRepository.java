package com.diplom.userservice.repository;

import com.diplom.userservice.entity.UserProfile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    List<UserProfile> findAllByUserIdIn(Collection<UUID> userIds);

    @Query("""
        SELECT p FROM UserProfile p
        WHERE LOWER(p.username) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY p.username ASC
        """)
    List<UserProfile> searchByUsernameOrFullName(@Param("q") String query, Pageable pageable);
}

