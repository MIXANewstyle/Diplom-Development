---
trigger: always_on
---

# Project Context for AI Agents

These rules apply to every task in this repository. Do not violate them
unless the user explicitly asks for an exception in a specific prompt.

## 1. Project Overview

Diploma project: AI-Mediated Therapy Platform. A microservices system
where users get AI-moderated therapy sessions, read content from verified
psychologists, and form support communities.

Microservices (separate Spring Boot modules):
- user-service: authentication, profiles, RBAC, friendships, author follows.
- content-service: posts, tags, comments, upvotes, feed, search.
- chat-service: AI-mediated chat rooms (solo and couple).
- billing-service: subscriptions and transactions.
- api-gateway: thin Spring Cloud Gateway router.

Currently implemented: user-service, content-service, chat-service (in work), api-gateway (routing only).

## 2. Tech Stack

- Java 21, Spring Boot 3.2.4, Maven multi-module (parent: backend/pom.xml).
- PostgreSQL 16 with Flyway migrations.
- Spring Data JPA + Hibernate (with @Version optimistic locking).
- Spring Security + jjwt 0.11.5 for JWT authentication.
- RabbitMQ (image rabbitmq:3-management-alpine) for async messaging.
- Redis 7 (planned for caching and counter hot-path).
- Lombok everywhere.
- Actuator + Prometheus + Grafana for observability.

Infrastructure runs via infrastructure/docker-compose.yml.

## 3. Architectural Principles

### Schema-per-service
Each service owns its own PostgreSQL schema:
- user_schema, content_schema, chat_schema, billing_schema.
- Tables are declared with @Table(name = "...", schema = "...").

### No cross-schema foreign keys
Cross-service references use UUID "soft links" only. NEVER create a
physical FK between schemas. Internal FKs within the same schema are fine
and expected.

### Transactional Outbox pattern
Every service has an outbox table (e.g., user_outbox_events). Business
mutations and event records are written in the same DB transaction. A
@Scheduled publisher polls PENDING rows, publishes to RabbitMQ, marks
them PROCESSED. Never publish to RabbitMQ directly from business code.

### Saga via events
Cross-service workflows happen via events on RabbitMQ. NEVER use
distributed transactions (2PC). NEVER call another service's database
directly.

### Mostly async, sometimes sync
Default to RabbitMQ events. Synchronous HTTP between services is allowed
only for read-time enrichment (e.g., content-service fetching usernames
from user-service in batch). Sync calls must be cached and tolerant to
peer outages.

## 4. Coding Conventions

### Package layout
For each service: com.diplom.{service-name}.{subpackage}
where subpackage is one of: controller, service, repository, entity, dto,
config, security, outbox, exception.

### Entities
- Use Lombok: @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder.
- @Id with @GeneratedValue(strategy = GenerationType.UUID) for UUID PKs.
- @Version on Integer fields for optimistic locking on mutable entities.
- Use OffsetDateTime for business timestamps; ZonedDateTime acceptable
  for outbox tables (matches existing UserOutboxEvent).
- @JdbcTypeCode(SqlTypes.JSON) for JSONB columns.

### DTOs
- Java records, not POJOs.
- Validation annotations from jakarta.validation when accepting input
  (@Email, @NotBlank, @Size).

### Services
- @Service, @RequiredArgsConstructor for constructor DI.
- @Transactional on write methods (default propagation).
- Throw domain-specific exceptions (e.g., EmailAlreadyTakenException),
  not raw RuntimeException. Centralized handling via @RestControllerAdvice.

### Controllers
- @RestController, @RequestMapping("/api/v1/...").
- Return ResponseEntity<T> with explicit HTTP statuses.
- Inject auth principal via @AuthenticationPrincipal CustomUserDetails.

### Logging
- Lombok @Slf4j, never System.out.println.
- log.error must include the exception as the last argument
  (e.g., log.error("Failed to publish event {}", id, ex)).

### Tests
- JUnit 5, Mockito for unit tests.
- Testcontainers for integration tests (PostgreSQL, RabbitMQ, Redis).

## 5. Database Rules

### Migrations
- Flyway, located at src/main/resources/db/migration.
- Naming: V{N}__{snake_case_description}.sql.
- Numbering is global per service and monotonically increasing. Never
  rename or rewrite an applied migration; always add a new one.
- All TIMESTAMP columns are TIMESTAMP WITH TIME ZONE.
- Lookup tables (statuses, roles, types) use INT PK with explicit values
  (no auto-increment) to keep IDs stable across environments.

### Outbox tables
- Every schema has its own outbox table with a B-tree index on the
  status column. Without this index, polling does full scans.

### Soft links
- Cross-service references are stored as UUID columns with a comment
  noting the target schema/table, but no FK constraint.

## 6. Security and Roles

### Role hierarchy (id, name, capability)
1. GUEST  - unauthenticated.
2. FREE   - registered, no subscription.
3. BASIC  - paid subscription, full content access.
4. AUTHOR - verified psychologist, can publish posts. Implies BASIC.
5. ADMIN  - moderator. Implies AUTHOR.

Spring Security RoleHierarchy: ADMIN > AUTHOR > BASIC > FREE > GUEST.

### Subscription gate
Content read access requires role >= BASIC. AUTHOR and ADMIN bypass the
subscription gate by virtue of the hierarchy.

### JWT
- JWT is issued by user-service on login.
- Claims: sub (email), userId (UUID), role (string name like "AUTHOR").
- Each service validates JWT independently via its own JwtAuthenticationFilter.
- API Gateway does NOT validate JWT; it routes only.

## 7. What Agents Must NOT Do

- Do not introduce cross-schema FKs.
- Do not add 2PC, XA transactions, or distributed locks.
- Do not bypass the Outbox by publishing to RabbitMQ from business code.
- Do not store mirror copies of another service's tables (use sync calls
  with cache, or events for invalidation).
- Do not modify api-gateway routing without explicit instruction.
- Do not rename, delete, or rewrite already-applied Flyway migrations.
- Do not introduce new top-level dependencies without confirming the
  trade-off with the user.
- Do not "opportunistically refactor" code that is not part of the task.