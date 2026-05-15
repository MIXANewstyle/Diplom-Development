package com.diplom.contentservice.repository;

import com.diplom.contentservice.entity.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
    Page<Tag> findAllByOrderByNameAsc(Pageable pageable);
    Page<Tag> findAllByNameStartingWithIgnoreCaseOrderByNameAsc(String prefix, Pageable pageable);
    boolean existsByName(String name);
}
