package com.diplom.userservice.repository;

import com.diplom.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    @org.springframework.data.jpa.repository.Query("""
        SELECT u FROM User u
        LEFT JOIN FETCH u.profile p
        WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(p.username) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
        """)
    org.springframework.data.domain.Page<User> searchUsers(@org.springframework.data.repository.query.Param("q") String query, org.springframework.data.domain.Pageable pageable);
}
