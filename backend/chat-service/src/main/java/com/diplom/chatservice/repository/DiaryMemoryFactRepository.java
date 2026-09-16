package com.diplom.chatservice.repository;

import com.diplom.chatservice.entity.DiaryMemoryFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiaryMemoryFactRepository extends JpaRepository<DiaryMemoryFact, UUID> {

    List<DiaryMemoryFact> findByOwnerUserIdOrderByCategoryIdAscCreatedAtAsc(UUID ownerUserId);

    Optional<DiaryMemoryFact> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    long countByOwnerUserId(UUID ownerUserId);
}
