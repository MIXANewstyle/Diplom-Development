package com.diplom.chatservice.service;

import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Background maintenance of the diary (same loop/exception pattern as {@link RoomSweepService}):
 * <ol>
 *   <li>close day rooms that are no longer writable (archive → async day summary → facts → index);</li>
 *   <li>catch up day summaries that failed (executor saturation, provider outage);</li>
 *   <li>catch up RAG indexing of user turns;</li>
 *   <li>generate due week/month summaries.</li>
 * </ol>
 * Every step is bounded per run so a backlog never floods the summary executor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiarySweepService {

    private static final int CLOSE_BATCH = 20;
    private static final int SUMMARY_CATCHUP_BATCH = 10;
    private static final int INDEX_CATCHUP_BATCH = 50;
    private static final int PERIOD_BATCH = 10;

    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final SummarizationService summarizationService;
    private final DiaryMemoryIndexer diaryMemoryIndexer;
    private final DiaryPeriodService diaryPeriodService;
    private final DiaryDateService dates;

    @Scheduled(fixedDelayString = "${chat.sweeps.diary-interval}", initialDelayString = "PT2M")
    public void sweep() {
        closeStaleDays();
        catchUpDaySummaries();
        try {
            diaryMemoryIndexer.catchUp(INDEX_CATCHUP_BATCH);
        } catch (Exception e) {
            log.warn("Diary index catch-up failed", e);
        }
        try {
            int n = diaryPeriodService.generateDueSummaries(PERIOD_BATCH);
            if (n > 0) log.info("Diary sweep generated {} period summar{}", n, n == 1 ? "y" : "ies");
        } catch (Exception e) {
            log.warn("Diary period sweep failed", e);
        }
    }

    /** Archive ACTIVE day rooms dated at or before today-2 (never a day that is still writable). */
    void closeStaleDays() {
        LocalDate threshold = dates.closeThreshold();
        Page<Room> candidates = roomRepository.findDiaryRoomsToArchive(threshold, PageRequest.of(0, CLOSE_BATCH));
        for (Room room : candidates.getContent()) {
            try {
                roomService.endSolo(room.getId(), room.getOwnerUserId());
                log.info("Diary sweep closed day {} (room {})", room.getDiaryDate(), room.getId());
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("Room {} modified concurrently, skipping in diary sweep", room.getId());
            } catch (Exception e) {
                log.warn("Error closing diary room {}", room.getId(), e);
            }
        }
    }

    /** Archived days whose turns were never fully folded into a summary. */
    void catchUpDaySummaries() {
        Page<Room> pending = roomRepository.findArchivedDiaryRoomsNeedingSummary(PageRequest.of(0, SUMMARY_CATCHUP_BATCH));
        for (Room room : pending.getContent()) {
            try {
                summarizationService.foldTurnsIntoSummary(room.getId(), Integer.MAX_VALUE);
            } catch (Exception e) {
                log.warn("Diary summary catch-up failed for room {}", room.getId(), e);
            }
        }
    }
}
