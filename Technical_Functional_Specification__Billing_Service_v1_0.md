# Functional Specification: Billing Service (billing_service)

This document specifies the business logic, constraints, payment-integration surface, distributed-transaction (Saga) behavior, and data model of the Billing Service — the microservice responsible for subscriptions, payments, promo codes, and the free trial within the AI-Mediated Therapy Platform. It is the financial core of the system: it owns the canonical subscription lifecycle and the transaction ledger, and it is the *origin* of the entitlement signal that the User Service turns into a role change.

The format mirrors the User Service (v2.0), Content Service (v6.0), and Chat Service (v1.0) specifications. The service body is written in English to match the existing repository; user-facing strings (promo codes, payment descriptions) are Russian because the platform's users are Russian and payment runs through a Russian provider.

# 0. MVP Scope

This specification targets the MVP release of Billing Service. As with the peer services, decisions favor implementation simplicity and time-to-market over peak performance and operational sophistication. Consequences of the MVP framing are explicitly marked. Deferred items appear in §14.

Three deliberate boundaries for MVP:

- **Payment is integrated behind a provider port, with a stub as the default implementation.** The full purchase pipeline (intent → external payment → webhook → activation) is built and exercised end-to-end against a `StubPaymentProvider`. Wiring a real Russian provider (YooKassa / ЮKassa is the reference target) is a configuration-and-credentials task, not a redesign — see §13.
- **One paid entitlement level — `BASIC`.** Every purchasable plan grants the `BASIC` tier, which the platform maps to the `BASIC` role. The `PREMIUM` tier exists in the dictionary but no plan grants it and no `PREMIUM` role exists yet (§4, §14).
- **No recurring billing.** Subscriptions are one-time, fixed-duration purchases (1 month / 3 months). There is no auto-renewal, no stored card, and no dunning. Renewal is an explicit new purchase by the user.

## 0.1. Product Assumptions (please confirm)

The brief fixed the core path; the following product decisions were made to complete the design and can be changed without structural impact:

1. **Plans and prices are example data**, stored in a `plans` table so they can be edited without code changes. MVP seeds `MONTH_1` (30 days) and `MONTH_3` (90 days); prices below (499 ₽ / 1299 ₽) are placeholders.
2. **Trial = 30 days of `BASIC`, once per user, ever.** Eligibility is tracked per user, not per email or device.
3. **A user cannot start a trial while a subscription is already active** (the trial is a way *in*, not a way to pause).
4. **Renewal stacks**: buying again while still active extends `expires_at` by the new plan's duration rather than resetting it.
5. **On expiry the user reverts to `FREE`** (no grace period in MVP).
6. **Promo codes are single-use per user** *and* globally capped by `max_uses`. Both constraints are enforced.
7. **A promo that drives the price to 0 ₽ activates immediately** without calling the payment provider (treated like a trial for settlement purposes).

# 1. Scope and Architectural Position

Billing Service owns `billing_schema` and is the single source of truth for subscription state, the transaction ledger, promo codes, and trial eligibility. It runs on port **8084** (user-service and api-gateway precede it; content-service is 8082, chat-service is 8083).

Core architectural principles, consistent with the platform:

- **No mirror tables of User Service data.** Billing holds UUID soft links to `user_schema.users(id)` and one small persistent projection that is *its own domain concern*, not a mirror: a `billing_accounts` row per user that records trial eligibility. It is populated purely by the `USER_REGISTERED` event (§7.2).
- **All cross-service identifiers are UUID soft links.** No physical foreign keys cross schema boundaries. Inside `billing_schema`, standard relational integrity (FKs, 3NF) holds.
- **The API Gateway stays a thin router.** Billing validates the JWT itself via its own `JwtAuthenticationFilter`, exactly as the peer services do (HS256, shared secret, 24-hour tokens — §2).
- **Entitlement is granted only after money is confirmed.** The Billing → User role Saga is intentionally *pessimistic*: the role is never upgraded on intent, only on a confirmed `SUCCESS`. This eliminates the need for a role-downgrade compensation on payment failure (§8).
- **Transactional Outbox for all cross-service events.** Business mutation and event emission commit in the same PostgreSQL transaction (§7).

Billing exposes: a public REST API (via the Gateway), a signature-verified payment webhook endpoint, an internal/admin API for promo and transaction management, and an asynchronous event stream via the outbox. It consumes two events from User Service.

# 2. Authentication and Authorization

- Billing validates the platform JWT locally via `JwtAuthenticationFilter` (algorithm `HS256`, the same server-side secret as the other services). It reads `userId` (UUID) and `role` (string name) claims. It does **not** issue tokens.
- The subscription gate is irrelevant *inbound* to Billing: a `FREE` user must be able to reach the purchase and trial endpoints (that is the whole point). Authorization on customer endpoints is therefore "any authenticated user."
- The webhook endpoint is **public to Spring Security** (`permitAll`) but authenticated at the application layer by **provider signature verification** (§6, §13). It carries no JWT.
- Admin endpoints under `/api/v1/admin/billing/**` require `ROLE_ADMIN`, enforced in `SecurityConfig` via `.requestMatchers("/api/v1/admin/billing/**").hasRole("ADMIN")`. This satisfies the product requirement that administrators can *view and moderate transactions*.
- Stub-only test endpoints (§6.4) are exposed only under the `dev`/`local` Spring profile (or gated to `ROLE_ADMIN`) and are never enabled in production.

# 3. Domain Model and Subscription Lifecycle

## 3.1. Entities

- **Plan** — a purchasable product: a `(tier, duration_days, price)` triple identified by a stable `code` (`MONTH_1`, `MONTH_3`, plus an internal `TRIAL_30`). Reference data, INT primary key, editable by admins.
- **Subscription** — the *current* entitlement state of one user. **Exactly one row per user** (`UNIQUE(user_id)`), created lazily on first activation and mutated in place thereafter (renewal extends it, expiry/cancellation closes it). Carries a Hibernate `@Version` for optimistic locking.
- **Transaction** — an immutable-by-intent ledger entry for every payment, trial grant, or refund. This is the audit history; the subscription row is only the current snapshot.
- **BillingAccount** — one row per user, the idempotent anchor created on registration. Holds `trial_used`.
- **PromoCode** / **PromoRedemption** — discount definitions and the per-user redemption record that enforces single-use.

## 3.2. Subscription status state machine

Statuses are the `sub_statuses` dictionary: `ACTIVE (1)`, `CANCELED (2)`, `EXPIRED (3)`.

- *(none)* → **ACTIVE**: first activation via paid `SUCCESS`, zero-amount promo, or trial.
- **ACTIVE** → **ACTIVE**: renewal — `expires_at += plan.duration_days` (stacking, §0.1.4). The row stays `BASIC`.
- **ACTIVE** → **EXPIRED**: the expiry sweep (§10) when `now() > expires_at`.
- **ACTIVE** → **CANCELED**: refund (§9.4) or account moderation/deletion (§7.2). Entitlement is revoked immediately.

Every transition that crosses the `FREE ⇄ BASIC` boundary emits exactly one `SUBSCRIPTION_CHANGED` event (§7.1). A pure renewal (BASIC → BASIC) does not need to emit it; MVP emits it anyway carrying the new `expiresAt`, which is harmless and keeps any downstream expiry cache fresh.

## 3.3. Tier ↔ Role mapping

| sub_tier (this service) | Effective role (User Service) |
|---|---|
| BASIC (1) | BASIC (3) |
| PREMIUM (2) | *reserved — no role yet (§14)* |
| *(no active subscription)* | FREE (2) |

The `SUBSCRIPTION_CHANGED.newTier` field carries the **role-level entitlement name** the User Service must apply — `"BASIC"` on activation, `"FREE"` on downgrade — not the internal `sub_tiers` row. For MVP the tier name and the target role name coincide; if `PREMIUM` is later given its own role, only the mapping table above changes.

# 4. Plans

`plans` is admin-editable reference data. MVP seed (prices are placeholders):

| id | code | tier | duration_days | price | currency | is_active | is_public |
|----|------|------|---------------|-------|----------|-----------|-----------|
| 1 | `MONTH_1` | BASIC | 30 | 499.00 | RUB | true | true |
| 2 | `MONTH_3` | BASIC | 90 | 1299.00 | RUB | true | true |
| 3 | `TRIAL_30` | BASIC | 30 | 0.00 | RUB | true | **false** |

- `is_public = false` plans (the trial) are not returned by the public plan list and cannot be bought via checkout; the trial is reached only through its dedicated endpoint (§5.2).
- `is_active = false` retires a plan from new purchases without deleting historical transactions that reference it.
- Duration is expressed in **days** for simplicity and predictability (a "month" = 30 days). Calendar-month arithmetic is deferred (§14).

# 5. Customer Flows

## 5.1. Purchase (paid checkout)

Endpoint: `POST /api/v1/billing/subscriptions/checkout`. Authenticated. Body: `{ planId, promoCode? }`. Optional header `Idempotency-Key`.

1. **Validate plan.** `planId` must reference an `is_active = true`, `is_public = true` plan, else `PlanNotFoundException` (404) / `PlanInactiveException` (400).
2. **Resolve price.** Start from `plan.price_amount`.
3. **Apply promo (if supplied) — with reservation.** Validate the code (§5.4). If valid, atomically reserve one use: `UPDATE promo_codes SET used_count = used_count + 1 WHERE id = ? AND used_count < max_uses` — zero rows affected ⇒ `PromoCodeExhaustedException` (409). Insert a `promo_redemptions` row `(promo_code_id, user_id)` — a PK conflict ⇒ `PromoAlreadyRedeemedException` (409, the user already holds a live redemption of this code). Compute `discount_amount` and `final_amount` (§5.4).
4. **Create the intent.** Insert a `transactions` row: `type = PURCHASE` (or `RENEWAL` if the user already has an `ACTIVE` subscription), `status = PENDING`, `base_amount`, `discount_amount`, `amount = final_amount`, `promo_code_id`, `provider = <configured>`, `idempotency_key` (if header present). Steps 3–4 commit in one DB transaction.
5. **Zero-amount short-circuit.** If `final_amount = 0.00` (full-coverage promo), do **not** call the provider: in the same flow mark the transaction `SUCCESS` and run activation (§8.1) immediately. Return `{ transactionId, status: "ACTIVATED" }`.
6. **Otherwise call the provider.** `paymentProvider.createPayment(amount, currency, { transactionId, userId, planCode })` returns `{ providerPaymentId, confirmationUrl }`. Persist `provider_payment_id` and (transiently) `confirmation_url` on the transaction. Return `{ transactionId, amount, currency, confirmationUrl }`.
7. **Client redirects** the user to `confirmationUrl`. Activation happens later, on the webhook (§8.1) — never synchronously here.

**Idempotency.** If `Idempotency-Key` matches an existing transaction for this user, return that transaction's intent instead of creating a new one (prevents double intents on double-clicks / retries).

## 5.2. Free trial

Endpoint: `POST /api/v1/billing/subscriptions/trial`. Authenticated. No body.

1. Load the caller's `billing_accounts` row. If `trial_used = true` ⇒ `TrialAlreadyUsedException` (409).
2. If the caller already has an `ACTIVE` subscription ⇒ `ActiveSubscriptionExistsException` (409).
3. In one DB transaction: set `trial_used = true` (guarded by `@Version`); run activation (§8.1) using the `TRIAL_30` plan; write a ledger `transactions` row `type = TRIAL, status = SUCCESS, amount = 0.00`; write `SUBSCRIPTION_CHANGED`.
4. Return `200 OK` with the new subscription view.

No payment provider is involved. The trial is the only path that sets `trial_used`.

## 5.3. Reading subscription and history

- `GET /api/v1/billing/me/subscription` → `{ tier, status, startedAt, expiresAt, trialUsed }`. If the user has never subscribed, returns `{ tier: null, status: "NONE", trialUsed }` (a synthetic view, not a stored row).
- `GET /api/v1/billing/me/transactions` → the caller's own ledger, newest first.

## 5.4. Promo code validation and discount math

A code is **valid for a user against a plan** iff all hold: it exists (case-insensitive — codes are stored upper-cased); `is_active = true`; `now()` within `[valid_from, valid_until]` when those are set; `used_count < max_uses`; and no `promo_redemptions` row exists for `(code, user)`.

Discount, by `discount_type`:

- `PERCENT`: `discount_amount = round(base_amount × value / 100, 2)`; `value ∈ (0, 100]`.
- `FIXED`: `discount_amount = min(value, base_amount)`.

`final_amount = max(base_amount − discount_amount, 0.00)`.

Endpoint `POST /api/v1/billing/promo/validate` — body `{ planId, code }` → `{ valid, discountType, discountAmount, finalAmount }`. This is a **preview only**: it performs the validation and math but **does not reserve** a use. Reservation happens only inside checkout (§5.1.3). This lets the UI show the discounted price before the user commits.

# 6. Payment Provider Integration

## 6.1. The provider port

Billing depends on a narrow interface, not a concrete gateway:

```
interface PaymentProvider {
    PaymentIntent createPayment(Money amount, String currency, PaymentMetadata meta);
    // -> { providerPaymentId, confirmationUrl }
    WebhookEvent parseAndVerify(Map<String,String> headers, String rawBody);
    // -> { providerPaymentId, status: SUCCEEDED|CANCELED, amount } ; throws WebhookSignatureInvalidException
    RefundResult refund(String providerPaymentId, Money amount); // §9.4 / §14
}
```

Two implementations ship:

- **`StubPaymentProvider`** (default, MVP). `createPayment` returns a synthetic `providerPaymentId` and a `confirmationUrl` pointing at the stub-confirm endpoint (§6.4). `parseAndVerify` trusts a shared test secret. Enables full end-to-end testing with no external dependency.
- **`YooKassaPaymentProvider`** (reference real implementation skeleton, §13). Selected by `billing.payment.provider=yookassa`.

The active implementation is chosen by configuration; no business code changes when switching.

## 6.2. The webhook

Endpoint: `POST /api/v1/billing/payments/webhook`. Public to Security; verified at the application layer.

- Read the raw body and provider headers, call `paymentProvider.parseAndVerify(...)`. On signature failure ⇒ `WebhookSignatureInvalidException` (400) and the event is dropped.
- Look up the transaction by `provider_payment_id`. Unknown id ⇒ log and return `200` (do not 404 a provider; some providers probe).
- **Idempotent terminal handling.** If the transaction is already `SUCCESS`/`FAILED`/`REFUNDED`, return `200` with no state change (providers redeliver). Otherwise apply the transition (§8).
- Always return `200` to the provider on a recognized, well-formed event so it stops retrying.

## 6.3. Currency and amounts

All money is `DECIMAL(10,2)` in **rubles** (not kopecks), currency `'RUB'`. Note for §13: YooKassa's API expects the amount as a string with two decimals and `"currency":"RUB"`, so the adapter formats accordingly at the boundary.

## 6.4. Stub-only endpoint (MVP, non-production)

`POST /api/v1/billing/payments/stub/confirm/{transactionId}` — simulates a provider `payment.succeeded` callback for local development and tests. Enabled only under the `dev`/`local` profile (or gated to `ROLE_ADMIN`). It calls the same internal activation path the real webhook uses, so the happy path is identical in test and production.

# 7. Distributed Event Synchronization (Transactional Outbox)

Billing implements the Transactional Outbox pattern identically to the peer services: every business event is persisted to `billing_outbox_events` in the same DB transaction as the entity mutation, then published to RabbitMQ by a background worker. This guarantees at-least-once delivery without distributed (2PC) transactions.

## 7.1. Events published

Exchange: topic exchange `billing.events.exchange`. All payloads are typed Java records serialized via the auto-configured Jackson `ObjectMapper`, each carrying `occurredAt: OffsetDateTime` for out-of-order detection.

| Event | Trigger | Payload | Consumers | Routing key |
|-------|---------|---------|-----------|-------------|
| `SUBSCRIPTION_CHANGED` | Entitlement crosses FREE⇄BASIC: activation (paid/trial/zero-amount), expiry, cancellation, refund. | `{ userId, newTier, expiresAt, occurredAt }` where `newTier ∈ {"BASIC","FREE"}` | **User Service** | `billing.subscription-changed` |
| `PAYMENT_SUCCEEDED` *(optional, analytics)* | Transaction → SUCCESS. | `{ userId, transactionId, planCode, amount, occurredAt }` | Admin/analytics *(consumer deferred, §14)* | `billing.payment-succeeded` |
| `PAYMENT_FAILED` *(optional, analytics)* | Transaction → FAILED. | `{ userId, transactionId, reason, occurredAt }` | Admin/analytics *(deferred)* | `billing.payment-failed` |

`SUBSCRIPTION_CHANGED` is the one contract the platform already depends on: the User Service v2.0 spec (§4.2, §6.4, §12) reserves a consumer for exactly this event — on receipt it calls `updateUserRole(userId, newRoleId)` and emits its own `ROLE_UPDATED`, which Content and Chat consume. **Billing must emit `SUBSCRIPTION_CHANGED` with this payload shape so that the existing User Service consumer can be implemented against it unchanged.**

## 7.2. Events consumed

Bound from `user.events.exchange`:

| Event | Routing key | Action | Idempotency |
|-------|-------------|--------|-------------|
| `USER_REGISTERED` | `user.registered` | Insert a `billing_accounts` row `(user_id, trial_used = false)`. This is the "initialize subscription state downstream" purpose named in the User Service spec. | PK on `user_id`; duplicate deliveries are ignored (insert-if-absent). |
| `ACCOUNT_MODERATED` | `user.account-moderated` | If the user has an `ACTIVE` subscription and the new status is `BANNED`/`DELETED`, set it to `CANCELED` and emit `SUBSCRIPTION_CHANGED{newTier:"FREE"}`. No automatic refund in MVP. | Re-deliveries are no-ops once already `CANCELED`. |

## 7.3. Operational requirements (mirrors User Service §7.3)

- **Publisher**: `OutboxPublisher.publishPendingEvents()` runs every 1 s (`@Scheduled(fixedDelay = 1000)`, `@Transactional`), fetches ≤100 `PENDING` events ordered by `created_at`, publishes each with its routing key, marks `PROCESSED` on success. On any exception the loop breaks; remaining events stay `PENDING` for the next tick (at-least-once).
- **Retention**: `cleanupProcessedEvents()` daily at 03:00 (cron `0 0 3 * * *`) deletes `PROCESSED` events older than 24 h.
- **Indexing**: B-tree on `billing_outbox_events.status` is mandatory.

# 8. The Activation Saga

## 8.1. Internal activation (single local transaction)

`activate(userId, plan, transaction)` is the shared core used by paid success, zero-amount promo, and trial. In **one** `@Transactional` boundary:

1. **Upsert the subscription** (optimistic-locked):
   - no existing row → insert `(user_id, tier = BASIC, status = ACTIVE, started_at = now(), expires_at = now() + plan.duration_days)`;
   - existing `ACTIVE` and not yet expired → `expires_at += plan.duration_days` (renewal, stacking);
   - existing `EXPIRED`/`CANCELED` → restart: `status = ACTIVE`, `started_at = now()`, `expires_at = now() + plan.duration_days`.
2. **Finalize the ledger**: transaction `status = SUCCESS` (already set for trial/zero-amount).
3. **Promo redemption** (paid path): link `promo_redemptions.transaction_id`; the reservation made at checkout (§5.1.3) is now permanent.
4. **Emit** `SUBSCRIPTION_CHANGED{ userId, newTier:"BASIC", expiresAt }` to the outbox.

Commit. The outbox publisher then drives the cross-service half of the Saga (§7.1 → User Service → `ROLE_UPDATED`).

## 8.2. End-to-end happy path (paid)

```
Client            Billing                     Provider        Rabbit         User Svc
  | checkout ------> reserve promo, PENDING txn
  |                  createPayment ----------->|
  | <-- confirmationUrl
  | --- pay -------------------------------->  |
  |                  <---- webhook(succeeded) --|
  |                  activate(): SUCCESS, sub ACTIVE,
  |                  emit SUBSCRIPTION_CHANGED ----------> (BASIC)
  |                                                         consume -> role FREE->BASIC
  |                                                         emit ROLE_UPDATED -> Content/Chat
```

Until the user's next JWT is issued they keep the old role for up to the token lifetime (24 h, MVP) — the same accepted staleness the User Service documents; refresh tokens (User Service §12) shorten it later.

## 8.3. Failure and compensation

- **Payment fails / is canceled / expires.** Webhook `payment.canceled` (or the stale-intent sweep, §10) transitions the transaction `PENDING → FAILED` and runs the **local promo compensation**: `UPDATE promo_codes SET used_count = used_count − 1 WHERE id = ?` and delete the `promo_redemptions` row, freeing the reserved use and letting the user retry the same code. **No role change occurs** — the role was never upgraded, so there is nothing to roll back on the User Service side. This is why the entitlement Saga is pessimistic.
- **User Service can't apply the role** (e.g., the user was banned between intent and success). Handled by at-least-once redelivery plus idempotent consumption; if the account is gone, the `ACCOUNT_MODERATED` path (§7.2) reconciles by canceling the subscription.
- **Refund** is the deliberate post-success downgrade path (§9.4): it emits `SUBSCRIPTION_CHANGED{newTier:"FREE"}`, the documented compensation that returns the user to `FREE`.

# 9. Admin / Internal API

All under `/api/v1/admin/billing/**`, `ROLE_ADMIN`.

## 9.1. Promo management

- `POST /promo` — create: `{ code, discountType, discountValue, maxUses, validFrom?, validUntil? }`. Code is upper-cased and must be unique (`PromoCodeAlreadyExistsException`, 409). Validation: `maxUses ≥ 1`, `discountValue > 0`, `PERCENT ⇒ value ≤ 100`.
- `GET /promo` — list with `used_count` / `max_uses`.
- `PATCH /promo/{id}` — toggle `is_active`, adjust `max_uses` / validity window (cannot lower `max_uses` below `used_count`).

## 9.2. Transaction moderation

- `GET /transactions` — filter by `userId`, `status`, date range, paginated. (Satisfies the product requirement "просмотр и модерация транзакций".)
- `GET /transactions/{id}` — full detail including provider ids.

## 9.3. Manual subscription override (break-glass)

- `POST /subscriptions/{userId}/grant` — admin grants `BASIC` for N days (support/goodwill). Writes a `transactions` row `type = PURCHASE, amount = 0` with an audit note, runs activation. Use is logged.

## 9.4. Refund

- `POST /transactions/{id}/refund` — calls `paymentProvider.refund(...)`, on success writes a `REFUNDED` ledger entry, sets the subscription `CANCELED`, emits `SUBSCRIPTION_CHANGED{newTier:"FREE"}`. The provider-side refund call is the part that depends on the real gateway; the ledger/Saga half is fully specified here. Partial/prorated refunds are deferred (§14).

# 10. Scheduled Jobs

| Job | Schedule (MVP) | Action |
|-----|----------------|--------|
| `OutboxPublisher` | every 1 s | Publish `PENDING` outbox events (§7.3). |
| `cleanupProcessedEvents` | daily 03:00 | Delete `PROCESSED` events > 24 h (§7.3). |
| `expireSubscriptions` | hourly | `ACTIVE` rows with `expires_at < now()` → `EXPIRED`; emit `SUBSCRIPTION_CHANGED{newTier:"FREE"}`. The downgrade path. |
| `expireStalePendingTransactions` | every 15 min | `PENDING` transactions older than `INTENT_TTL` (default 60 min, ≥ the provider's payment lifetime) → `FAILED` + promo compensation (§8.3). Defends against intents the provider never resolves. |

All emission jobs write through the outbox, never publishing to RabbitMQ directly.

# 11. Database Schema (billing_schema)

Items marked **[NEW]** extend the original Technical Database Specification; unmarked items match it. Migrations follow the project's Flyway convention (`V1__billing_init.sql`, …).

## 11.1. Dictionaries

| Table | Values |
|-------|--------|
| `sub_tiers` | 1=BASIC, 2=PREMIUM |
| `sub_statuses` | 1=ACTIVE, 2=CANCELED, 3=EXPIRED |
| `txn_statuses` | 1=PENDING, 2=SUCCESS, 3=FAILED, 4=REFUNDED |
| `txn_types` **[NEW]** | 1=PURCHASE, 2=RENEWAL, 3=TRIAL, 4=REFUND |
| `discount_types` **[NEW]** | 1=PERCENT, 2=FIXED |

Each dictionary is `(id INT PK, name VARCHAR(50) UNIQUE NOT NULL)`.

## 11.2. Core tables

**`plans`** **[NEW]**

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | INT | PK | |
| `code` | VARCHAR(50) | UNIQUE, NOT NULL | `MONTH_1`, `MONTH_3`, `TRIAL_30` |
| `tier_id` | INT | NOT NULL, FK → `sub_tiers(id)` | BASIC for all MVP plans |
| `duration_days` | INT | NOT NULL, CHECK > 0 | |
| `price_amount` | DECIMAL(10,2) | NOT NULL, CHECK ≥ 0 | RUB |
| `currency` | CHAR(3) | NOT NULL DEFAULT 'RUB' | |
| `is_active` | BOOLEAN | NOT NULL DEFAULT TRUE | retire without deleting |
| `is_public` | BOOLEAN | NOT NULL DEFAULT TRUE | trial = false |

**`billing_accounts`** **[NEW]**

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `user_id` | UUID | PK | Soft link to `user_schema.users(id)`. Created on `USER_REGISTERED`. |
| `trial_used` | BOOLEAN | NOT NULL DEFAULT FALSE | one-time trial gate |
| `created_at` | TIMESTAMPTZ | NOT NULL | |
| `version` | INT | NOT NULL DEFAULT 0 | `@Version` |

**`subscriptions`** (extended)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | app-generated |
| `user_id` | UUID | **UNIQUE**, NOT NULL **[NEW: UNIQUE]** | Soft link; one current row per user |
| `tier_id` | INT | NOT NULL, FK → `sub_tiers(id)` | |
| `status_id` | INT | NOT NULL, FK → `sub_statuses(id)` | |
| `started_at` | TIMESTAMPTZ | NOT NULL **[NEW]** | start of current period |
| `expires_at` | TIMESTAMPTZ | NOT NULL | |
| `created_at` | TIMESTAMPTZ | NOT NULL **[NEW]** | |
| `updated_at` | TIMESTAMPTZ | NOT NULL **[NEW]** | `@UpdateTimestamp` |
| `version` | INT | NOT NULL DEFAULT 0 | `@Version` |

Index: B-tree on `(status_id, expires_at)` for the expiry sweep.

**`transactions`** (extended ledger)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK **[NEW: internal app-generated, not the gateway id]** | see Decision #11 |
| `user_id` | UUID | NOT NULL, B-tree index | Soft link |
| `plan_id` | INT | NULL, FK → `plans(id)` **[NEW]** | NULL only for refunds |
| `type_id` | INT | NOT NULL, FK → `txn_types(id)` **[NEW]** | |
| `status_id` | INT | NOT NULL, FK → `txn_statuses(id)` | |
| `base_amount` | DECIMAL(10,2) | NOT NULL **[NEW]** | list price before discount |
| `discount_amount` | DECIMAL(10,2) | NOT NULL DEFAULT 0 **[NEW]** | |
| `amount` | DECIMAL(10,2) | NOT NULL | final charged |
| `currency` | CHAR(3) | NOT NULL DEFAULT 'RUB' **[NEW]** | |
| `promo_code_id` | UUID | NULL, FK → `promo_codes(id)` **[NEW]** | |
| `provider` | VARCHAR(50) | NOT NULL **[NEW]** | `STUB` / `YOOKASSA` |
| `provider_payment_id` | VARCHAR(255) | UNIQUE, NULL **[NEW]** | gateway reference; webhook lookup key |
| `idempotency_key` | VARCHAR(255) | UNIQUE, NULL **[NEW]** | client double-submit guard |
| `created_at` | TIMESTAMPTZ | NOT NULL | |
| `updated_at` | TIMESTAMPTZ | NOT NULL **[NEW]** | |
| `version` | INT | NOT NULL DEFAULT 0 **[NEW]** | `@Version` |

Indexes: `(provider_payment_id)` unique; `(user_id, created_at)`; `(status_id, created_at)` for the stale-intent sweep.

**`promo_codes`** **[NEW]**

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `code` | VARCHAR(64) | UNIQUE, NOT NULL | stored UPPER-cased |
| `discount_type_id` | INT | NOT NULL, FK → `discount_types(id)` | |
| `discount_value` | DECIMAL(10,2) | NOT NULL, CHECK > 0 | percent or rubles |
| `max_uses` | INT | NOT NULL, CHECK ≥ 1 | global cap |
| `used_count` | INT | NOT NULL DEFAULT 0, CHECK `used_count <= max_uses` | reservation counter |
| `valid_from` | TIMESTAMPTZ | NULL | |
| `valid_until` | TIMESTAMPTZ | NULL | |
| `is_active` | BOOLEAN | NOT NULL DEFAULT TRUE | |
| `created_at` | TIMESTAMPTZ | NOT NULL | |
| `version` | INT | NOT NULL DEFAULT 0 | `@Version` |

**`promo_redemptions`** **[NEW]**

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `promo_code_id` | UUID | PK, FK → `promo_codes(id)` | composite PK part 1 |
| `user_id` | UUID | PK | composite PK part 2 (soft link) → enforces single-use per user |
| `transaction_id` | UUID | NULL, FK → `transactions(id)` | set on success |
| `redeemed_at` | TIMESTAMPTZ | NOT NULL | |

Index: B-tree on `user_id`.

**`billing_outbox_events`** (unchanged shape)

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK |
| `event_type` | VARCHAR(100) | NOT NULL |
| `payload` | JSONB | NOT NULL |
| `status` | VARCHAR(50) | NOT NULL, B-tree index |
| `created_at` | TIMESTAMPTZ | NOT NULL |

# 12. API Surface Summary

## 12.1. Public API (via API Gateway)

| Method & Path | Auth | Purpose |
|---------------|------|---------|
| `GET /api/v1/billing/plans` | Authenticated | List public, active plans with prices. |
| `GET /api/v1/billing/me/subscription` | Authenticated | Caller's current subscription state. |
| `GET /api/v1/billing/me/transactions` | Authenticated | Caller's transaction history. |
| `POST /api/v1/billing/promo/validate` | Authenticated | Preview a promo against a plan (no reservation). |
| `POST /api/v1/billing/subscriptions/checkout` | Authenticated | Start a paid purchase; returns `confirmationUrl`. |
| `POST /api/v1/billing/subscriptions/trial` | Authenticated | Claim the one-time free month. |

## 12.2. Webhook (no JWT; signature-verified)

| Method & Path | Auth | Purpose |
|---------------|------|---------|
| `POST /api/v1/billing/payments/webhook` | Signature | Provider payment callback (idempotent). |

## 12.3. Internal / Admin API

| Method & Path | Auth | Purpose |
|---------------|------|---------|
| `POST /api/v1/admin/billing/promo` | `ROLE_ADMIN` | Create a promo code. |
| `GET /api/v1/admin/billing/promo` | `ROLE_ADMIN` | List promo codes + usage. |
| `PATCH /api/v1/admin/billing/promo/{id}` | `ROLE_ADMIN` | Update / deactivate a promo. |
| `GET /api/v1/admin/billing/transactions` | `ROLE_ADMIN` | View / filter transactions. |
| `POST /api/v1/admin/billing/transactions/{id}/refund` | `ROLE_ADMIN` | Refund + downgrade. |
| `POST /api/v1/admin/billing/subscriptions/{userId}/grant` | `ROLE_ADMIN` | Manual goodwill grant. |

## 12.4. Stub-only (dev/local profile)

| Method & Path | Auth | Purpose |
|---------------|------|---------|
| `POST /api/v1/billing/payments/stub/confirm/{transactionId}` | dev / `ROLE_ADMIN` | Simulate provider success. |

# 13. Going Live with Real Payments (YooKassa reference)

The stub keeps the whole pipeline real except the external call. To switch to YooKassa (ЮKassa), implement `YooKassaPaymentProvider` against the port (§6.1) and supply configuration — no business-logic changes:

- **Config**: `billing.payment.provider=yookassa`, `yookassa.shop-id`, `yookassa.secret-key`, `yookassa.return-url` (where the user lands after paying).
- **`createPayment`**: `POST https://api.yookassa.ru/v3/payments` with HTTP Basic auth (`shopId:secretKey`), an `Idempotence-Key` header (reuse the transaction id), a body containing `amount {value, currency}`, `capture: true`, `confirmation {type: "redirect", return_url}`, and a `metadata` object with `transactionId`. Map the response `id` → `provider_payment_id` and `confirmation.confirmation_url` → `confirmationUrl`.
- **Webhook (`parseAndVerify`)**: register the notification URL in the YooKassa dashboard; handle `payment.succeeded` (→ `SUCCEEDED`) and `payment.canceled` (→ `CANCELED`). Verify authenticity (IP allow-list and/or by re-fetching the payment via the API). Map the payment object's `id` back to the transaction.
- **`refund`**: `POST /v3/refunds` with the `payment_id` and amount.
- **Fiscalization (54-ФЗ)**: Russian law requires a fiscal receipt for consumer payments. YooKassa can generate it if you include a `receipt` object (customer contact + item with VAT) in `createPayment`. Confirm the exact `receipt`/VAT fields and current request/response shapes against the live YooKassa API docs at integration time — treat the field-level details above as the integration *shape*, not a frozen contract.

Other Russian providers (CloudPayments, Т-Касса/Tinkoff, Robokassa) fit the same port; only the adapter changes.

# 14. Deferred Post-MVP

- **Recurring billing / auto-renewal** with saved payment methods and dunning. Introduces a `PENDING_CANCELLATION` status and a user-initiated "cancel auto-renew" endpoint (meaningless without recurring billing, hence deferred).
- **`PREMIUM` tier and role.** Dictionary value exists; add a `PREMIUM` role in User Service, a plan that grants it, and extend the tier↔role map (§3.3).
- **Grace period** before downgrade on expiry (e.g., 3 days of read-only access).
- **Calendar-month durations** (`INTERVAL '1 month'`) instead of fixed 30/90 days.
- **Prorated upgrades and partial refunds.**
- **Analytics/admin consumer** for `PAYMENT_SUCCEEDED` / `PAYMENT_FAILED`.
- **Explicit 409 handler** for `ObjectOptimisticLockingFailureException` (currently falls through to the 500 catch-all, as in User Service).
- **UUIDv7 primary keys** to reduce B-tree fragmentation (platform-wide note).
- **Partitioning `transactions` by month** (declarative partitioning) — flagged in the Technical Database Specification as a scale concern for this table.
- **Rate limiting** on checkout / trial / promo-validate at the Gateway.
- **Multi-currency.**
- **Webhook DLQ / richer retry** beyond at-least-once redelivery.

# 15. Decisions Log

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Billing owns the canonical subscription lifecycle; the User Service role is a *derived* entitlement updated via `SUBSCRIPTION_CHANGED`. | Single source of truth for money/state; the role stays the cheap authorization flag the other services already check (`hasRole('BASIC')`). |
| 2 | Entitlement is granted only on confirmed `SUCCESS` (pessimistic Saga), never on intent. | Removes the need for a role-downgrade compensation on payment failure; the only downgrade paths are expiry, cancellation, and refund. |
| 3 | One `subscriptions` row per user (`UNIQUE(user_id)`), mutated in place; history lives in `transactions`. | Matches the original schema's single `expires_at` + `@Version`; renewal is a date extension, not a new row. |
| 4 | Renewal **stacks** onto remaining time. | Users buying again before expiry never lose paid days. |
| 5 | Trial grants a real `BASIC` subscription for 30 days and is gated by `billing_accounts.trial_used`, set in the same transaction. | One uniform activation path; the gate is a single boolean guarded by optimistic locking, so the "once ever" rule can't be raced. |
| 6 | Promo use is **reserved at checkout** and **compensated on failure** (decrement + delete redemption). | Honors the global `max_uses` cap under concurrency and gives a clean local compensating transaction, matching the platform's Saga theme. |
| 7 | Single-use-per-user enforced by the `promo_redemptions` composite PK. | Authorization/uniqueness built into the schema, not a check that can be forgotten. |
| 8 | Promo `validate` previews without reserving; reservation happens only inside checkout. | The UI can show a discounted price without burning a use on browsing. |
| 9 | A zero-amount (fully discounted) purchase activates immediately without the provider. | No external call for a 0 ₽ charge; same settlement path as the trial. |
| 10 | The webhook is `permitAll` to Security but verified by provider signature; terminal states are idempotent. | Providers carry no JWT and redeliver; signature + idempotency is the correct contract. |
| 11 | `transactions.id` is an internal app UUID; the gateway reference is a separate `provider_payment_id`. | The row must exist *before* the provider call, and provider ids are provider-formatted strings, not UUIDs. (Deliberate correction of the original DB note "id = Payment Gateway reference ID".) |
| 12 | Payment lives behind a `PaymentProvider` port with a stub default and a YooKassa reference adapter. | The brief asks for a working skeleton now and a drop-in real provider later; configuration selects the implementation. |
| 13 | Plans/prices/durations are data in a `plans` table, not constants. | Operators edit pricing without a deploy; the trial is just a non-public plan. |
| 14 | `SUBSCRIPTION_CHANGED.newTier` carries the target **role** name (`BASIC`/`FREE`), aligned with the consumer the User Service v2.0 spec already reserves. | Zero-change integration with the already-built service. |
| 15 | All cross-service writes go through the outbox; scheduled jobs never publish to RabbitMQ directly. | Uniform at-least-once delivery and atomicity with the business mutation. |
| 16 | A stale-intent sweep fails `PENDING` transactions older than the provider TTL and compensates their promo reservation. | Guarantees abandoned checkouts release reserved promo uses even if the provider never sends a terminal callback. |

# 16. Cross-Cutting Concerns

## 16.1. Concurrency
`subscriptions`, `transactions`, `promo_codes`, and `billing_accounts` carry `@Version`. The promo reservation uses a conditional `UPDATE ... WHERE used_count < max_uses` so the cap holds without table locks. All multi-row mutations run inside `@Transactional` and commit atomically with their outbox event.

## 16.2. Error handling
All exceptions flow through `GlobalExceptionHandler` (`@RestControllerAdvice`), returning the same JSON shape as the peer services (no `path` field, no stack traces to clients; 4xx logged WARN, 5xx ERROR).

| Exception | HTTP |
|-----------|------|
| `PlanNotFoundException`, `TransactionNotFoundException`, `PromoCodeNotFoundException` | 404 |
| `PlanInactiveException`, `PromoCodeInactiveException`, `PromoCodeExpiredException`, `MethodArgumentNotValidException`, `HttpMessageNotReadableException` | 400 |
| `TrialAlreadyUsedException`, `ActiveSubscriptionExistsException`, `PromoCodeExhaustedException`, `PromoAlreadyRedeemedException`, `PromoCodeAlreadyExistsException`, `DataIntegrityViolationException` | 409 |
| `WebhookSignatureInvalidException` | 400 |
| `PaymentProviderException` (provider unreachable / 5xx) | 502 |
| Unauthenticated / access denied (custom entry-point & handler) | 401 / 403 |
| `Exception` (catch-all) | 500 |

## 16.3. Identifier strategy
UUIDs via Hibernate `GenerationType.UUID` (UUIDv4 in MVP; UUIDv7 deferred, §14).

## 16.4. Observability
SLF4J + Logback (INFO default); Actuator health + Prometheus. Recommended billing metrics (post-MVP): payment success/failure rate, intent→success latency, outbox lag, active-subscription count, promo redemption rate.

## 16.5. Money safety
`DECIMAL`, never floating point. All arithmetic rounds to 2 decimals (`RoundingMode.HALF_UP`). Amounts are validated `≥ 0` and currency is `RUB` throughout MVP.
