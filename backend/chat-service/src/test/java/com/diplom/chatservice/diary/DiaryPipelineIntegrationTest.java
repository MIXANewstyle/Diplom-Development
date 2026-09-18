package com.diplom.chatservice.diary;

import com.diplom.chatservice.diary.support.DiaryTestBeans;
import com.diplom.chatservice.diary.support.FakeLlmClient;
import com.diplom.chatservice.diary.support.FakeLlmClient.Kind;
import com.diplom.chatservice.diary.support.MutableClock;
import com.diplom.chatservice.dto.SubmitTurnRequest;
import com.diplom.chatservice.dto.SubmitTurnResponse;
import com.diplom.chatservice.dto.diary.DiaryCalendarResponse;
import com.diplom.chatservice.dto.diary.DiaryDayResponse;
import com.diplom.chatservice.dto.diary.DiaryMemoriesResponse;
import com.diplom.chatservice.dto.diary.DiaryPeriodResponse;
import com.diplom.chatservice.entity.DiaryMemoryFact;
import com.diplom.chatservice.entity.DiaryPeriod;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.exception.DiaryDayClosedException;
import com.diplom.chatservice.exception.InvalidRoomStateException;
import com.diplom.chatservice.llm.LlmRequest;
import com.diplom.chatservice.repository.DiaryMemoryChunkJdbcRepository;
import com.diplom.chatservice.repository.DiaryMemoryFactRepository;
import com.diplom.chatservice.repository.DiaryPeriodRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.security.CustomUserDetails;
import com.diplom.chatservice.service.DiaryMemoryFactService;
import com.diplom.chatservice.service.DiaryPeriodService;
import com.diplom.chatservice.service.DiaryRetrievalService;
import com.diplom.chatservice.service.DiaryService;
import com.diplom.chatservice.service.DiarySweepService;
import com.diplom.chatservice.service.TurnOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end run of the diary memory pipeline against real Postgres (pgvector), Redis and RabbitMQ,
 * with the clock, the chat model and the embeddings provider replaced by deterministic test doubles
 * ({@link DiaryTestBeans}). Days pass in seconds:
 *
 * <pre>
 *  10 Mar  write → context has no memory yet → close day → summary, facts, RAG chunks
 *  11 Mar  write → context carries yesterday's summary + facts, RAG quotes 10 Mar verbatim
 *  13 Mar  sweep closes 11 Mar (stale)
 *  19 Mar  sweep generates the week 9–15 Mar; author adds "своими словами"; on-demand regeneration
 *   3 Apr  sweep generates March
 *   5 Apr  write → context carries the March summary and "a year ago" (5 Apr 2025)
 * </pre>
 *
 * Needs Docker; skipped (not failed) where it is unavailable:
 * {@code mvn -f backend/pom.xml -pl chat-service test -Dtest=DiaryPipelineIntegrationTest}.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(DiaryTestBeans.class)
@TestPropertySource(properties = {
        "chat.sweeps.diary-interval=PT1H",   // the test drives the sweep by hand
        "chat.llm.log-payload=false",
        "user-service.base-url=http://127.0.0.1:1"   // profile/about lookups fail fast and soft
})
class DiaryPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    @ServiceConnection
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-alpine");

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(30);
    private static final int STATUS_ACTIVE = 3;
    private static final int STATUS_ARCHIVED = 5;

    @Autowired DiaryService diaryService;
    @Autowired TurnOrchestrationService turns;
    @Autowired DiarySweepService sweep;
    @Autowired DiaryPeriodService periodService;
    @Autowired DiaryMemoryFactService factService;
    @Autowired RoomRepository rooms;
    @Autowired DiaryPeriodRepository periods;
    @Autowired DiaryMemoryFactRepository facts;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired FakeLlmClient llm;
    @Autowired MutableClock clock;

    private final UUID owner = UUID.randomUUID();
    private final CustomUserDetails principal = new CustomUserDetails(owner, "diary@test.local", "ADMIN");

    private static final String DAY1_TEXT = "Поссорился с Лерой, боюсь что она уйдёт";
    private static final String DAY2_TEXT = "Снова думаю про ссору с Лерой и страх что она уйдёт";

    @Test
    void diaryMemoryFlowsFromDayToWeekToMonthAndBack() {
        // ---------- 10 March: first entry, nothing to remember yet ----------
        LocalDate day1 = LocalDate.of(2026, 3, 10);
        clock.set(day1);
        DiaryDayResponse d1 = diaryService.openDay(owner, day1);
        assertThat(d1.writable()).isTrue();
        assertThat(d1.status()).isEqualTo("ACTIVE");
        assertThat(diaryService.openDay(owner, day1).roomId()).as("open is idempotent").isEqualTo(d1.roomId());

        SubmitTurnResponse r1 = turns.submitTurn(d1.roomId(), principal, new SubmitTurnRequest(DAY1_TEXT));
        assertThat(r1.assistantTurn().content()).isNotBlank();

        LlmRequest chat1 = llm.last(Kind.CHAT).request();
        assertThat(chat1.systemText()).contains("Тип комнаты: дневник. Дата записи: 10 марта 2026");
        assertThat(chat1.system().get(0).cacheBoundary()).as("system prefix is a cache boundary").isTrue();
        assertThat(chat1.systemText()).doesNotContain("Предыдущие дни:");
        assertThat(FakeLlmClient.lastUserMessage(chat1)).as("no memory block on the very first entry").isEqualTo(DAY1_TEXT);

        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(chunkCount(DiaryMemoryChunkJdbcRepository.SOURCE_USER_TURN)).isEqualTo(1));
        assertThat(rooms.findById(d1.roomId()).orElseThrow().getStatusId()).isEqualTo(STATUS_ACTIVE);

        // ---------- close the day: summary → facts → RAG index, all asynchronous ----------
        DiaryDayResponse closed = diaryService.closeDay(owner, day1);
        assertThat(closed.status()).isEqualTo("ARCHIVED");
        assertThat(closed.writable()).isFalse();
        assertThat(closed.closedAt()).isNotNull();

        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> {
            Room room = rooms.findById(d1.roomId()).orElseThrow();
            assertThat(room.getRunningSummary()).contains("Итог:").contains("Лерой");
            assertThat(room.getSummarizedThroughSeq()).isEqualTo(2);
            List<DiaryMemoryFact> f = facts.findByOwnerUserIdOrderByCategoryIdAscCreatedAtAsc(owner);
            assertThat(f).extracting(DiaryMemoryFact::getContent).containsExactly(FakeLlmClient.FACT_TEXT);
            assertThat(chunkCount(DiaryMemoryChunkJdbcRepository.SOURCE_DAY_SUMMARY)).isEqualTo(1);
        });
        assertThat(llm.last(Kind.DAY_SUMMARY).input()).contains("[Автор]: " + DAY1_TEXT);
        assertThatThrownBy(() -> diaryService.closeDay(owner, day1)).isInstanceOf(InvalidRoomStateException.class);
        assertThat(diaryService.getDay(owner, day1).summary()).contains("Итог:");

        // ---------- 11 March: the author is remembered ----------
        LocalDate day2 = day1.plusDays(1);
        clock.set(day2);
        DiaryDayResponse d2 = diaryService.openDay(owner, day2);
        turns.submitTurn(d2.roomId(), principal, new SubmitTurnRequest(DAY2_TEXT));

        LlmRequest chat2 = llm.last(Kind.CHAT).request();
        String system2 = chat2.systemText();
        assertThat(system2).contains("Предыдущие дни:").contains("— 10 марта 2026:").contains("Итог: " + DAY1_TEXT);
        assertThat(system2).contains("Открытые нити: разговор с Лерой");
        assertThat(system2).contains("Что известно об авторе").contains(FakeLlmClient.FACT_TEXT);
        String user2 = FakeLlmClient.lastUserMessage(chat2);
        assertThat(user2).startsWith(DiaryRetrievalService.RAG_HEADER);
        assertThat(user2).contains("10 марта 2026 (запись): «" + DAY1_TEXT + "»");
        assertThat(user2).contains(DiaryRetrievalService.RAG_FOOTER).endsWith("Запись:\n" + DAY2_TEXT);

        // ---------- 13 March: the sweep closes the stale day ----------
        clock.set(day1.plusDays(3));
        DiarySweepService.SweepResult s1 = sweep.sweep();
        assertThat(s1.closedDays()).isEqualTo(1);
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> {
            Room room = rooms.findById(d2.roomId()).orElseThrow();
            assertThat(room.getStatusId()).isEqualTo(STATUS_ARCHIVED);
            assertThat(room.getRunningSummary()).contains(DAY2_TEXT);
        });
        assertThat(sweep.sweep().closedDays()).as("second pass is a no-op").isZero();

        // ---------- 19 March: the week 9–15 March is due ----------
        clock.set(LocalDate.of(2026, 3, 19));
        DiarySweepService.SweepResult s2 = sweep.sweep();
        assertThat(s2.periodSummariesGenerated()).isEqualTo(1);
        LocalDate weekStart = LocalDate.of(2026, 3, 9);
        DiaryPeriod week = periods.findByOwnerUserIdAndPeriodTypeIdAndPeriodStart(owner, DiaryPeriod.TYPE_WEEK, weekStart).orElseThrow();
        assertThat(week.getAutoSummary()).contains(FakeLlmClient.WEEK_MARK);
        assertThat(week.getAutoSummaryThrough()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(llm.last(Kind.WEEK_SUMMARY).input()).contains("— 10 марта 2026:").contains("— 11 марта 2026:");
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(chunkCount(DiaryMemoryChunkJdbcRepository.SOURCE_PERIOD_SUMMARY)).isEqualTo(1));
        assertThat(sweep.sweep().periodSummariesGenerated()).as("already complete → not regenerated").isZero();

        // author's own words feed the regenerated summary
        periodService.saveUserSummary(owner, DiaryPeriod.TYPE_WEEK, weekStart, "Тяжёлая неделя, много думал о Лере");
        DiaryPeriodResponse regenerated = diaryService.generateAutoSummary(owner, DiaryPeriod.TYPE_WEEK, weekStart);
        assertThat(regenerated.userSummary()).isEqualTo("Тяжёлая неделя, много думал о Лере");
        assertThat(regenerated.autoSummary()).contains(FakeLlmClient.WEEK_MARK);
        assertThat(llm.last(Kind.WEEK_SUMMARY).input()).contains("своими словами автора").contains("Тяжёлая неделя");

        // ---------- 3 April: March is due ----------
        clock.set(LocalDate.of(2026, 4, 3));
        DiarySweepService.SweepResult s3 = sweep.sweep();
        assertThat(s3.periodSummariesGenerated()).isEqualTo(1);
        DiaryPeriod month = periods.findByOwnerUserIdAndPeriodTypeIdAndPeriodStart(owner, DiaryPeriod.TYPE_MONTH, LocalDate.of(2026, 3, 1)).orElseThrow();
        assertThat(month.getAutoSummary()).contains(FakeLlmClient.MONTH_MARK);
        assertThat(month.getAutoSummaryThrough()).isEqualTo(LocalDate.of(2026, 3, 31));
        String monthInput = llm.last(Kind.MONTH_SUMMARY).input();
        assertThat(monthInput).startsWith("Месяц: 1–31 марта 2026");
        assertThat(monthInput).contains("Итоги недель своими словами автора:").contains("Тяжёлая неделя");

        // ---------- 5 April: long memory + "a year ago" ----------
        LocalDate yearAgo = LocalDate.of(2025, 4, 5);
        rooms.save(Room.builder().typeId(2).soloModeId(2).statusId(STATUS_ARCHIVED).ownerUserId(owner)
                .aiModel("test").diaryDate(yearAgo).title(yearAgo.toString())
                .runningSummary("Итог: год назад было спокойно").summarizedThroughSeq(0).build());

        LocalDate day3 = LocalDate.of(2026, 4, 5);
        clock.set(day3);
        DiaryDayResponse d3 = diaryService.openDay(owner, day3);
        turns.submitTurn(d3.roomId(), principal, new SubmitTurnRequest("Как прошёл этот год"));
        String system3 = llm.last(Kind.CHAT).request().systemText();
        assertThat(system3).contains("Итог прошлого месяца (март 2026), автоматически:").contains(FakeLlmClient.MONTH_MARK);
        assertThat(system3).contains("В этот день год назад (5 апреля 2025):").contains("год назад было спокойно");

        DiaryMemoriesResponse memories = diaryService.memoriesFor(owner, day3);
        assertThat(memories.items()).extracting(DiaryMemoriesResponse.Memory::kind).containsExactly("YEAR_AGO");
        assertThat(memories.items().get(0).summary()).contains("год назад было спокойно");

        // ---------- calendar view of March ----------
        DiaryCalendarResponse march = diaryService.calendar(owner, YearMonth.of(2026, 3));
        assertThat(march.serverToday()).isEqualTo(day3);
        assertThat(march.days()).extracting(DiaryCalendarResponse.Day::date).containsExactly(day1, day2);
        assertThat(march.days()).allSatisfy(d -> {
            assertThat(d.hasSummary()).isTrue();
            assertThat(d.writable()).isFalse();
        });
        assertThat(march.weeks()).filteredOn(w -> w.start().equals(weekStart)).singleElement()
                .satisfies(w -> {
                    assertThat(w.autoSummary()).contains(FakeLlmClient.WEEK_MARK);
                    assertThat(w.userSummary()).contains("Тяжёлая неделя");
                    assertThat(w.closed()).isTrue();
                });
        assertThat(march.monthPeriod().autoSummary()).contains(FakeLlmClient.MONTH_MARK);

        // ---------- the author corrects and forgets a fact ----------
        DiaryMemoryFact fact = facts.findByOwnerUserIdOrderByCategoryIdAscCreatedAtAsc(owner).get(0);
        factService.update(owner, fact.getId(), "Лера — партнёрша, вместе два года");
        assertThat(factService.list(owner)).extracting(DiaryMemoryFact::getContent).containsExactly("Лера — партнёрша, вместе два года");
        factService.delete(owner, fact.getId());
        assertThat(factService.list(owner)).isEmpty();

        // ---------- guards ----------
        assertThatThrownBy(() -> diaryService.openDay(owner, LocalDate.of(2026, 3, 20)))
                .as("a past day with no entry cannot be opened").isInstanceOf(DiaryDayClosedException.class);
        LocalDate emptyDay = day3.plusDays(1); // tomorrow for a UTC+ client: inside the ±1 window
        diaryService.openDay(owner, emptyDay);
        assertThatThrownBy(() -> diaryService.closeDay(owner, emptyDay))
                .as("nothing to summarize on a day with no turns")
                .isInstanceOf(InvalidRoomStateException.class).hasMessageContaining("no entries");
    }

    private int chunkCount(int sourceId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_schema.diary_memory_chunks WHERE owner_user_id = :o AND source_id = :s",
                new MapSqlParameterSource("o", owner).addValue("s", sourceId), Integer.class);
        return n == null ? 0 : n;
    }
}
