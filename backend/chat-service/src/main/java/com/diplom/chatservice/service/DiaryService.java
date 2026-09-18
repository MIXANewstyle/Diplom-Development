package com.diplom.chatservice.service;

import com.diplom.chatservice.dto.diary.DiaryCalendarResponse;
import com.diplom.chatservice.dto.diary.DiaryDayResponse;
import com.diplom.chatservice.dto.diary.DiaryMemoriesResponse;
import com.diplom.chatservice.dto.diary.DiaryPeriodResponse;
import com.diplom.chatservice.entity.DiaryPeriod;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.exception.DiaryDayClosedException;
import com.diplom.chatservice.exception.InvalidRoomStateException;
import com.diplom.chatservice.exception.RoomNotFoundException;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Diary days and the calendar view. A day is a SOLO room in DIARY mode keyed by
 * (owner, diary_date); turns go through the regular room turn pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private static final int STATUS_ACTIVE = 3;

    private final RoomRepository roomRepository;
    private final TurnRepository turnRepository;
    private final RoomService roomService;
    private final RoomMapper roomMapper;
    private final ContextSnapshotService contextSnapshotService;
    private final DiaryDateService dates;
    private final DiaryPeriodService periodService;

    // ==================== days ====================

    /**
     * Idempotent get-or-create of the day room. Only "today" and "yesterday" (±1 day around the
     * server's UTC date) can be opened for writing; older days are read via {@link #getDay}.
     */
    public DiaryDayResponse openDay(UUID owner, LocalDate date) {
        Optional<Room> existing = roomRepository.findByOwnerUserIdAndDiaryDate(owner, date);
        if (existing.isPresent()) {
            return toDayResponse(existing.get());
        }
        if (!dates.isWritable(date)) {
            throw new DiaryDayClosedException("Diary day " + date + " is closed; only today and yesterday can be written");
        }
        Room room;
        try {
            room = roomService.createDiaryRoom(owner, date);
        } catch (DataIntegrityViolationException race) {
            // two tabs opened the same day at once — the unique index won, re-select
            room = roomRepository.findByOwnerUserIdAndDiaryDate(owner, date).orElseThrow(() -> race);
        }
        // Snapshot the author's profile/about for this day (network calls outside any tx; idempotent)
        contextSnapshotService.captureForRoom(room.getId());
        log.info("Diary day {} opened for user {} (room {})", date, owner, room.getId());
        return toDayResponse(room);
    }

    @Transactional(readOnly = true)
    public DiaryDayResponse getDay(UUID owner, LocalDate date) {
        Room room = roomRepository.findByOwnerUserIdAndDiaryDate(owner, date)
                .orElseThrow(() -> new RoomNotFoundException("No diary entry for " + date));
        return toDayResponse(room);
    }

    /**
     * Finish the day now instead of waiting for the sweep: the room is archived, which triggers the
     * day summary, fact extraction and RAG indexing asynchronously. Works for any of the owner's
     * ACTIVE days regardless of the writable window (the sweep and the admin API reuse it).
     */
    public DiaryDayResponse closeDay(UUID owner, LocalDate date) {
        Room room = roomRepository.findByOwnerUserIdAndDiaryDate(owner, date)
                .orElseThrow(() -> new RoomNotFoundException("No diary entry for " + date));
        if (room.getStatusId() != STATUS_ACTIVE) {
            throw new InvalidRoomStateException("Diary day is already closed");
        }
        if (turnRepository.countByRoomId(room.getId()) == 0) {
            throw new InvalidRoomStateException("Nothing to summarize: the day has no entries");
        }
        roomService.endSolo(room.getId(), owner);
        log.info("Diary day {} closed by user {} (room {})", date, owner, room.getId());
        return toDayResponse(roomRepository.findById(room.getId()).orElseThrow());
    }

    public void deleteDay(UUID owner, LocalDate date, Object principal) {
        roomRepository.findByOwnerUserIdAndDiaryDate(owner, date)
                .ifPresent(room -> roomService.deleteRoom(room.getId(), principal));
    }

    // ==================== calendar ====================

    @Transactional(readOnly = true)
    public DiaryCalendarResponse calendar(UUID owner, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<Room> rooms = roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(owner, from, to);
        List<DiaryCalendarResponse.Day> days = rooms.stream()
                .map(r -> new DiaryCalendarResponse.Day(
                        r.getDiaryDate(),
                        r.getId(),
                        roomMapper.roomStatusName(r.getStatusId()),
                        turnRepository.countByRoomId(r.getId()),
                        r.getRunningSummary() != null && !r.getRunningSummary().isBlank(),
                        dates.isWritable(r.getDiaryDate()) && r.getStatusId() == STATUS_ACTIVE))
                .toList();

        // ISO weeks overlapping the month
        Map<LocalDate, DiaryPeriod> weekRows = periodService.findOverlapping(owner, DiaryPeriod.TYPE_WEEK, from, to).stream()
                .collect(Collectors.toMap(DiaryPeriod::getPeriodStart, Function.identity()));
        List<DiaryPeriodResponse> weeks = new ArrayList<>();
        for (LocalDate ws = dates.weekStart(from); !ws.isAfter(to); ws = ws.plusWeeks(1)) {
            weeks.add(toPeriodResponse(DiaryPeriod.TYPE_WEEK, ws, ws.plusDays(6), weekRows.get(ws)));
        }
        DiaryPeriodResponse monthRow = toPeriodResponse(DiaryPeriod.TYPE_MONTH, from, to,
                periodService.find(owner, DiaryPeriod.TYPE_MONTH, from).orElse(null));

        return new DiaryCalendarResponse(month.toString(), dates.today(), days, weeks, monthRow);
    }

    // ==================== memories ("в этот день год назад") ====================

    @Transactional(readOnly = true)
    public DiaryMemoriesResponse memoriesFor(UUID owner, LocalDate date) {
        List<DiaryMemoriesResponse.Memory> items = new ArrayList<>();
        memory(owner, date.minusYears(1), "YEAR_AGO").ifPresent(items::add);
        memory(owner, date.minusMonths(1), "MONTH_AGO").ifPresent(items::add);
        return new DiaryMemoriesResponse(date, items);
    }

    private Optional<DiaryMemoriesResponse.Memory> memory(UUID owner, LocalDate date, String kind) {
        return roomRepository.findByOwnerUserIdAndDiaryDate(owner, date)
                .map(r -> new DiaryMemoriesResponse.Memory(kind, date, r.getId(), r.getRunningSummary(),
                        turnRepository.countByRoomId(r.getId())));
    }

    // ==================== periods ====================

    public DiaryPeriodResponse getPeriod(UUID owner, int periodTypeId, LocalDate anyDate) {
        LocalDate start = periodService.normalizeStart(periodTypeId, anyDate);
        return toPeriodResponse(periodTypeId, start, periodService.periodEnd(periodTypeId, start),
                periodService.find(owner, periodTypeId, start).orElse(null));
    }

    public DiaryPeriodResponse saveUserSummary(UUID owner, int periodTypeId, LocalDate anyDate, String text) {
        DiaryPeriod p = periodService.saveUserSummary(owner, periodTypeId, anyDate, text);
        return toPeriodResponse(periodTypeId, p.getPeriodStart(), p.getPeriodEnd(), p);
    }

    public DiaryPeriodResponse generateAutoSummary(UUID owner, int periodTypeId, LocalDate anyDate) {
        DiaryPeriod p = periodService.generateOnDemand(owner, periodTypeId, anyDate);
        return toPeriodResponse(periodTypeId, p.getPeriodStart(), p.getPeriodEnd(), p);
    }

    // ==================== mapping ====================

    private DiaryDayResponse toDayResponse(Room room) {
        return new DiaryDayResponse(
                room.getId(),
                room.getDiaryDate(),
                roomMapper.roomStatusName(room.getStatusId()),
                dates.isWritable(room.getDiaryDate()) && room.getStatusId() == STATUS_ACTIVE,
                room.getRunningSummary(),
                turnRepository.countByRoomId(room.getId()),
                room.getTitle(),
                room.getCreatedAt(),
                room.getEndedAt()
        );
    }

    private DiaryPeriodResponse toPeriodResponse(int typeId, LocalDate start, LocalDate end, DiaryPeriod p) {
        return new DiaryPeriodResponse(
                typeId == DiaryPeriod.TYPE_MONTH ? "MONTH" : "WEEK",
                start,
                end,
                p != null ? p.getAutoSummary() : null,
                p != null ? p.getAutoSummaryThrough() : null,
                p != null ? p.getAutoGeneratedAt() : null,
                p != null ? p.getUserSummary() : null,
                p != null ? p.getUserUpdatedAt() : null,
                dates.isPeriodDue(end)
        );
    }
}
