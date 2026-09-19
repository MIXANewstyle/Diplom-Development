package com.diplom.chatservice.controller;

import com.diplom.chatservice.config.ChatGuestProperties;
import com.diplom.chatservice.config.SecurityConfig;
import com.diplom.chatservice.dto.diary.DiaryCalendarResponse;
import com.diplom.chatservice.dto.diary.DiaryDayResponse;
import com.diplom.chatservice.exception.DiaryDayClosedException;
import com.diplom.chatservice.exception.InvalidRoomStateException;
import com.diplom.chatservice.exception.RoomNotFoundException;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.security.JsonAccessDeniedHandler;
import com.diplom.chatservice.security.JsonAuthenticationEntryPoint;
import com.diplom.chatservice.security.JwtService;
import com.diplom.chatservice.service.DiaryMemoryFactService;
import com.diplom.chatservice.service.DiaryPeriodService;
import com.diplom.chatservice.service.DiaryService;
import com.diplom.chatservice.service.DiarySweepService;
import com.diplom.chatservice.service.SummarizationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Key;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The diary API seen from outside: JWT → role hierarchy → {@code @PreAuthorize} → controller →
 * error mapping. The service layer is mocked; what is under test is everything around it, which the
 * pipeline test deliberately bypasses by calling services directly.
 *
 * <p>Two properties matter most here and are easy to break silently:
 * <ul>
 *   <li>AUTHOR and ADMIN reach a {@code hasRole('BASIC')} endpoint only because
 *       {@link SecurityConfig#roleHierarchy()} exists — dropping that bean locks admins out of
 *       their own diary, which is exactly the shape of a bug this project already hit;</li>
 *   <li>the owner of every diary operation comes from the token, never from the request, so one
 *       author cannot address another author's days.</li>
 * </ul>
 */
@WebMvcTest(controllers = {DiaryController.class, AdminDiaryController.class})
@Import({SecurityConfig.class, JwtService.class, JsonAccessDeniedHandler.class, JsonAuthenticationEntryPoint.class})
@EnableConfigurationProperties(ChatGuestProperties.class)
@TestPropertySource(properties = {
        "jwt.secret=" + DiaryApiSecurityTest.SECRET,
        "chat.guest.token-ttl=PT24H"
})
class DiaryApiSecurityTest {

    /** Same shape as the deployed secret: Base64, long enough for HS256. */
    static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private static final UUID CALLER = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    @Autowired MockMvc mvc;

    @MockBean DiaryService diaryService;
    @MockBean DiaryMemoryFactService factService;
    @MockBean DiarySweepService diarySweepService;
    @MockBean DiaryPeriodService diaryPeriodService;
    @MockBean SummarizationService summarizationService;
    @MockBean RoomRepository roomRepository;

    // ==================== authentication ====================

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/diary/calendar").param("month", "2026-03"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(diaryService);
    }

    @ParameterizedTest(name = "a token that is {0} is rejected")
    @ValueSource(strings = {"garbage", "a.b.c"})
    void unparseableTokenIsUnauthorized(String token) throws Exception {
        mvc.perform(get("/api/v1/diary/calendar").param("month", "2026-03")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(diaryService);
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/diary/calendar").param("month", "2026-03")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, "BASIC", Duration.ofMinutes(-5))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(diaryService);
    }

    @Test
    void invitedGuestCannotReachTheDiary() throws Exception {
        String roomScoped = Jwts.builder()
                .setClaims(Map.of("scope", "guest"))
                .setSubject(UUID.randomUUID().toString())
                .setAudience("room:" + UUID.randomUUID())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();

        mvc.perform(get("/api/v1/diary/calendar").param("month", "2026-03")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + roomScoped))
                .andExpect(status().isForbidden());
        verifyNoInteractions(diaryService);
    }

    // ==================== authorization ====================

    @ParameterizedTest(name = "{0} may not read the diary")
    @ValueSource(strings = {"GUEST", "FREE"})
    void belowBasicIsForbidden(String role) throws Exception {
        mvc.perform(get("/api/v1/diary/calendar").param("month", "2026-03")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
        verifyNoInteractions(diaryService);
    }

    @ParameterizedTest(name = "{0} reads the diary of the token's owner")
    @ValueSource(strings = {"BASIC", "AUTHOR", "ADMIN"})
    void basicAndAboveReadTheirOwnDiary(String role) throws Exception {
        given(diaryService.calendar(any(), any())).willReturn(
                new DiaryCalendarResponse("2026-03", DATE, List.of(), List.of(), null));

        mvc.perform(get("/api/v1/diary/calendar").param("month", "2026-03")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-03"));

        verify(diaryService).calendar(CALLER, YearMonth.of(2026, 3));
    }

    @Test
    void theDayBelongsToTheCallerNotToTheRequest() throws Exception {
        given(diaryService.openDay(CALLER, DATE)).willReturn(day("ACTIVE", true));

        mvc.perform(put("/api/v1/diary/days/2026-03-10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, "BASIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-03-10"))
                .andExpect(jsonPath("$.writable").value(true));

        verify(diaryService).openDay(CALLER, DATE);
    }

    // ==================== error contract the client relies on ====================

    @Test
    void closingADayWithNoEntriesIsAConflict() throws Exception {
        given(diaryService.closeDay(CALLER, DATE))
                .willThrow(new InvalidRoomStateException("Nothing to summarize: the day has no entries"));

        mvc.perform(post("/api/v1/diary/days/2026-03-10/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, "BASIC")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Nothing to summarize: the day has no entries"));
    }

    @Test
    void openingAClosedDayIsAConflict() throws Exception {
        given(diaryService.openDay(CALLER, DATE)).willThrow(new DiaryDayClosedException("Diary day 2026-03-10 is closed"));

        mvc.perform(put("/api/v1/diary/days/2026-03-10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, "BASIC")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Diary day 2026-03-10 is closed"));
    }

    @Test
    void readingADayThatWasNeverWrittenIsNotFound() throws Exception {
        given(diaryService.getDay(CALLER, DATE)).willThrow(new RoomNotFoundException("No diary entry for 2026-03-10"));

        mvc.perform(get("/api/v1/diary/days/2026-03-10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, "BASIC")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownPeriodTypeIsARequestError() throws Exception {
        mvc.perform(get("/api/v1/diary/periods").param("type", "DECADE").param("start", "2026-03-09")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, "BASIC")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(diaryService);
    }

    // ==================== operator endpoints ====================

    @ParameterizedTest(name = "{0} may not run the operator endpoints")
    @ValueSource(strings = {"FREE", "BASIC", "AUTHOR"})
    void operatorEndpointsAreAdminOnly(String role) throws Exception {
        mvc.perform(post("/internal/v1/admin/diary/sweep")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(diarySweepService);
    }

    @Test
    void adminRunsTheSweep() throws Exception {
        given(diarySweepService.sweep()).willReturn(new DiarySweepService.SweepResult(2, 1, 7, 1));

        mvc.perform(post("/internal/v1/admin/diary/sweep")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedDays").value(2))
                .andExpect(jsonPath("$.summariesCaughtUp").value(1))
                .andExpect(jsonPath("$.turnsIndexed").value(7))
                .andExpect(jsonPath("$.periodSummariesGenerated").value(1));
    }

    @Test
    void adminActsOnTheUserFromThePath() throws Exception {
        UUID author = UUID.randomUUID();
        given(diaryService.closeDay(author, DATE)).willReturn(day("ARCHIVED", false));

        mvc.perform(post("/internal/v1/admin/diary/users/" + author + "/days/2026-03-10/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        verify(diaryService).closeDay(author, DATE);
    }

    @Test
    void generatingASummaryForAnEmptyPeriodIsUnprocessable() throws Exception {
        LocalDate weekStart = LocalDate.of(2026, 3, 9);
        UUID author = UUID.randomUUID();
        given(diaryPeriodService.normalizeStart(anyInt(), any())).willReturn(weekStart);
        given(diaryPeriodService.generateAutoSummary(any(), anyInt(), any())).willReturn(null);

        mvc.perform(post("/internal/v1/admin/diary/users/" + author + "/periods/WEEK/2026-03-09/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CALLER, "ADMIN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("No summarized days in this period yet"));
    }

    // ==================== helpers ====================

    private static DiaryDayResponse day(String status, boolean writable) {
        return new DiaryDayResponse(UUID.randomUUID(), DATE, status, writable, null, 0, DATE.toString(),
                OffsetDateTime.now(), writable ? null : OffsetDateTime.now());
    }

    private static String token(UUID userId, String role) {
        return token(userId, role, Duration.ofHours(1));
    }

    /** Mirrors the token user-service issues: subject = email, {@code userId} and {@code role} claims. */
    private static String token(UUID userId, String role, Duration ttl) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject("author@example.com")
                .claim("userId", userId.toString())
                .claim("role", role)
                .setIssuedAt(new Date(now - 60_000))
                .setExpiration(new Date(now + ttl.toMillis()))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private static Key signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }
}
