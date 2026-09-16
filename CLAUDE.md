# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Diploma project: an AI-mediated therapy & content platform. Maven monorepo — root `pom.xml`
aggregates `backend/` (5 Spring Boot 3.2.4 / Java 21 modules), plus a standalone Vite + React 18
SPA in `frontend/`.

`rules/spectest.md` is an always-on agent rules file; its invariants are summarized below and it
is the authority if the two ever disagree.

## Commands

### Backend (no Maven wrapper — use system `mvn`, run from repo root)

```bash
mvn -q -DskipTests -pl backend/user-service -am package   # build one service + its parents
mvn -DskipTests package                                   # build everything
mvn -pl backend/chat-service test                         # tests for one module
mvn -pl backend/chat-service test -Dtest=ClassName#methodName      # single test
mvn -pl backend/user-service spring-boot:run              # run one service locally
```

Local runs need the infra up first: `docker compose -f infrastructure/docker-compose.yml up -d`
(Postgres on **5433**, Redis 6379, RabbitMQ 5672/15672, Prometheus 9090, Grafana 3000). Default
`application.yml` values point at `localhost` and those ports, so no env vars are needed for dev.

There are currently **no backend test sources** — `src/test` does not exist in any module. New
tests go under JUnit 5 + Mockito; Testcontainers for integration.

### Frontend (`cd frontend`)

```bash
npm install
npm run dev          # :5173, expects the gateway on :8080
npm run build        # tsc -b && vite build
npm run lint
npm run test         # vitest watch
npm run test:run     # single pass (CI)
npx vitest run src/features/chat/lib/__tests__/optimisticTurns.test.ts   # one file
npx vitest run -t "name of test"                                        # one test
```

### Full stack

```bash
cp .env.example .env
docker compose -f docker-compose.prod.yml up -d --build   # app on :80 via Caddy
```
See `DEPLOY.md`. Backend Dockerfiles build from the **repo root** context (they copy `pom.xml` +
`backend/`), which is why `docker-compose.prod.yml` sets `context: .`.

## Architecture

```
Browser ──HTTP──┐                    ┌── user-service    :8081  user_schema
                ├─ Caddy :80 ────────┤── content-service :8082  content_schema
Browser ──WS────┘   /api,/internal → api-gateway :8080 ─┤── chat-service    :8083  chat_schema
                    /ws → chat-service directly         └── billing-service :8084  billing_schema
                                     Postgres · Redis · RabbitMQ
```

**api-gateway** is a *thin router only* — two classes, path predicates, CORS for `/api/**`. It does
not validate JWT and does no API composition. Backend URLs come from `USER_SERVICE_URL` etc.
Do not change gateway routing without explicit instruction.

**WebSocket traffic bypasses the gateway in production**: Caddy proxies `/ws*` straight to
chat-service (`frontend/Caddyfile`), because gateway CORS on top of chat-service's own headers
produced duplicate `Access-Control-Allow-Origin` and broke the SockJS handshake. The `/ws/**`
gateway route exists for local dev only.

**Auth is decentralized.** user-service issues the JWT (claims: `sub`=email, `userId`, `role`);
each service verifies it independently with its own `JwtAuthenticationFilter` and the shared
`JWT_SECRET`. Role hierarchy `ADMIN > AUTHOR > BASIC > FREE > GUEST`; content reads require
>= BASIC.

**chat-service also owns the LLM integration** — there is no separate AI service. `llm/OpenAiCompatibleLlmClient`
is called **synchronously** from `TurnOrchestrationService.executeAiStep`, not via RabbitMQ. Any
OpenAI-compatible endpoint works; defaults target Gemini. The mediator/solo system prompts live in
`backend/chat-service/src/main/resources/application.yml` under `chat.llm.prompts` — edit them there,
not in Java. `WebSocketConfig` documents the STOMP destination contract (`/topic/rooms/{id}`,
`/app/...`, `/user/queue/rooms/{id}`); `AuthChannelInterceptor` accepts both normal JWTs and
`GuestPrincipal` (invited partner with a room-scoped guest token).

### Inter-service communication

**Async (default) — RabbitMQ topic exchanges + transactional outbox.** Each service owns an
exchange (`user.events.exchange`, `billing.…`, `content.…`, `chat.…`) and a durable queue named
`<service>.<source>-events.queue` for events it consumes. Business code **never** calls
`RabbitTemplate` — it writes an outbox row in the same transaction; a `@Scheduled(fixedDelay=1000)`
`OutboxPublisher` drains PENDING → PROCESSED and prunes after 24h. Wired bindings today:

| Routing key | Publisher → Consumer(s) | Purpose |
|---|---|---|
| `user.registered` | user → billing | create billing account |
| `billing.subscription-changed` | billing → user | flip role FREE ↔ BASIC |
| `user.follow-added` / `follow-removed` | user → content | follow projection for the feed |
| `user.account-moderated` | user → content, chat | ban blocklist |
| `user.role-updated` | user → chat | role cache |
| `user.friendship-accepted` | user → chat | friend projection for couple rooms |

Other keys (`post.published`, `chat.room-archived`, `billing.payment-*`, `user.profile-changed`, …)
are published but have no consumer yet — adding one means adding a `Binding` bean in the consuming
service's `RabbitMQConfig`.

**Sync — `RestTemplate` (not OpenFeign, despite `spec.md`), read-time enrichment only.**
`content-service.UserServiceClient` calls user-service's *public* API forwarding the caller's Bearer
token (`TokenExtractor`), 2s/3s timeouts. `chat-service`'s `InternalUserBatchClient` / `PsychProfileClient`
call `/internal/v1/users/**`, which is guarded by the `X-Internal-Api-Key` header
(`InternalApiKeyFilter`), not by JWT. Sync calls must be cached and tolerant of peer outages.

**Redis is state, not just cache**: message drafts, presence, rate limits (`chat.limits.*`),
profile/role caches, moderation blocklist — plus a `room.events.{roomId}` pub/sub channel that
`RoomBroadcaster` publishes to and `RoomEventsRelayListener` replays into each instance's in-memory
STOMP broker (groundwork for running chat-service multi-instance).

### Data

One Postgres cluster, **schema per service**, each with its own Flyway history and
`ddl-auto: validate`. Hard rules:

- No cross-schema foreign keys — cross-service references are plain UUID "soft links" with a comment.
- No 2PC/XA, no distributed locks, no reading another service's tables.
- No mirrored copies of another service's tables; use sync-with-cache or event-driven invalidation.
- Migrations `V{N}__{snake_case}.sql` under `src/main/resources/db/migration`, numbering global per
  service. Never rename or edit an applied migration — add a new one.
- All timestamps `TIMESTAMP WITH TIME ZONE`. Lookup tables (roles, statuses, types) use explicit
  INT PKs, no auto-increment, so IDs are stable across environments.
- Every schema keeps its own outbox table with a B-tree index on `status`.

## Conventions

- Packages: `com.diplom.{service}.{controller|service|repository|entity|dto|config|security|outbox|exception}`.
- Entities: Lombok builders, `@GeneratedValue(strategy = GenerationType.UUID)`, `@Version` for
  optimistic locking on mutable entities, `@JdbcTypeCode(SqlTypes.JSON)` for JSONB,
  `OffsetDateTime` for business timestamps (`ZonedDateTime` in outbox tables).
- DTOs are Java records with `jakarta.validation` annotations on input.
- Services: `@RequiredArgsConstructor` constructor DI, `@Transactional` on writes, domain-specific
  exceptions handled centrally in a `@RestControllerAdvice`.
- Controllers return `ResponseEntity<T>`; principal via `@AuthenticationPrincipal CustomUserDetails`.
- `@Slf4j` logging; pass the exception as the **last** argument to `log.error`.
- Don't opportunistically refactor code outside the task, and confirm before adding a new top-level
  dependency.

## spec.md is aspirational

`spec.md` describes the intended design; several parts diverge from the code. Trust the code:
Next.js → actually Vite SPA; separate AI Therapy Service over RabbitMQ → LLM inside chat-service,
synchronous; OpenFeign → `RestTemplate`; API composition at the gateway → done in content-service;
Saga compensating transactions → not implemented (subscription activation is a one-way chain).
The PlantUML in `docs/diagrams/` reflects the real code.
