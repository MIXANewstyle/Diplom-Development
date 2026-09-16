package com.diplom.chatservice.service;

import com.diplom.chatservice.dto.diary.DiaryDayResponse;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.exception.DiaryDayClosedException;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    @Mock RoomRepository roomRepository;
    @Mock TurnRepository turnRepository;
    @Mock RoomService roomService;
    @Mock ContextSnapshotService contextSnapshotService;
    @Mock DiaryPeriodService periodService;

    private final UUID owner = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 9, 16);
    private DiaryService service;

    @BeforeEach
    void setUp() {
        DiaryDateService dates = new DiaryDateService(Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC));
        service = new DiaryService(roomRepository, turnRepository, roomService, new RoomMapper(), contextSnapshotService, dates, periodService);
    }

    private Room room(LocalDate date) {
        return Room.builder().id(UUID.randomUUID()).ownerUserId(owner).typeId(2).soloModeId(2).statusId(3)
                .aiModel("m").diaryDate(date).title(date.toString()).build();
    }

    @Test
    void openDayCreatesRoomAndCapturesSnapshot() {
        Room created = room(today);
        when(roomRepository.findByOwnerUserIdAndDiaryDate(owner, today)).thenReturn(Optional.empty());
        when(roomService.createDiaryRoom(owner, today)).thenReturn(created);
        when(turnRepository.countByRoomId(created.getId())).thenReturn(0L);

        DiaryDayResponse day = service.openDay(owner, today);

        assertThat(day.roomId()).isEqualTo(created.getId());
        assertThat(day.writable()).isTrue();
        assertThat(day.status()).isEqualTo("ACTIVE");
        verify(contextSnapshotService).captureForRoom(created.getId());
    }

    @Test
    void openDayIsIdempotentWhenRoomAlreadyExists() {
        Room existing = room(today);
        when(roomRepository.findByOwnerUserIdAndDiaryDate(owner, today)).thenReturn(Optional.of(existing));

        DiaryDayResponse day = service.openDay(owner, today);

        assertThat(day.roomId()).isEqualTo(existing.getId());
        verify(roomService, never()).createDiaryRoom(any(), any());
    }

    @Test
    void openDaySurvivesUniqueIndexRaceByReselecting() {
        Room winner = room(today);
        when(roomRepository.findByOwnerUserIdAndDiaryDate(owner, today))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(roomService.createDiaryRoom(owner, today)).thenThrow(new DataIntegrityViolationException("uq_rooms_owner_diary_date"));

        DiaryDayResponse day = service.openDay(owner, today);

        assertThat(day.roomId()).isEqualTo(winner.getId());
    }

    @Test
    void openDayRejectsClosedDatesButStillReturnsExistingOnes() {
        LocalDate old = today.minusDays(5);
        when(roomRepository.findByOwnerUserIdAndDiaryDate(owner, old)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.openDay(owner, old)).isInstanceOf(DiaryDayClosedException.class);
        verify(roomService, never()).createDiaryRoom(any(), any());

        Room archived = room(old);
        archived.setStatusId(5);
        when(roomRepository.findByOwnerUserIdAndDiaryDate(owner, old)).thenReturn(Optional.of(archived));
        DiaryDayResponse day = service.openDay(owner, old);
        assertThat(day.writable()).isFalse();
        assertThat(day.status()).isEqualTo("ARCHIVED");
    }
}
