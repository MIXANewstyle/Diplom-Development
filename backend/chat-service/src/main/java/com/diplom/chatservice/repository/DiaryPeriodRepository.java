package com.diplom.chatservice.repository;

import com.diplom.chatservice.entity.DiaryPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiaryPeriodRepository extends JpaRepository<DiaryPeriod, UUID> {

    Optional<DiaryPeriod> findByOwnerUserIdAndPeriodTypeIdAndPeriodStart(UUID ownerUserId, Integer periodTypeId, LocalDate periodStart);

    /** Periods of one type overlapping [from, to] (period_start <= to AND period_end >= from). */
    List<DiaryPeriod> findByOwnerUserIdAndPeriodTypeIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByPeriodStartAsc(
            UUID ownerUserId, Integer periodTypeId, LocalDate to, LocalDate from);
}
