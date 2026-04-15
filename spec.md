Project Specification: AI-Mediated Therapy & Content Platform
1. Product Overview
SaaS-платформа психологической помощи, объединяющая инновационные ИИ-инструменты для саморефлексии и парной терапии с системой умного поиска специалистов через контент. Платформа построена на микросервисной архитектуре (событийно-ориентированный подход).

Ключевые модули:

AI Couple Therapy: Синхронный чат для двух пользователей с ИИ-медиатором. Пошаговая (turn-based) логика передачи хода.

AI Solo Diary: Индивидуальный чат с ИИ для глубокой саморефлексии.

Content Matchmaking: Лента постов от верифицированных психологов со сложной системой фильтрации для поиска «своего» специалиста.

2. User Roles & Access (RBAC)
Система использует JWT-токены для разграничения прав доступа на уровне API Gateway и микросервисов:

Guest: Доступ к лендингу, регистрации и авторизации.

Free User: Управление профилем. Попытка доступа к ИИ-чатам или ленте вызывает редирект на Paywall.

Basic Subscription: Доступ к чтению ленты, созданию Solo и Couple чатов с ИИ.

Author: Все права Basic + возможность создавать, редактировать и удалять собственные посты. Статус выдается только после ручной верификации заявки (флаг is_verified_author).

3. Technology Stack
Frontend:

Framework: Next.js (App Router), React, TypeScript.

Styling: Tailwind CSS, shadcn/ui.

Backend Core (Microservices):

Language/Framework: Java 21+, Spring Boot 3.

Gateway: Spring Cloud Gateway.

Inter-service Sync: Spring Cloud OpenFeign.

Inter-service Async: Spring Cloud Stream + RabbitMQ.

Data & Infrastructure:

Primary Database: PostgreSQL (Schema-per-service pattern).

In-memory Cache / State: Redis (хранение эфемерного состояния чатов, кэш ленты, rate limiting).

Message Broker: RabbitMQ.

Migrations: Flyway или Liquibase.

Deployment: Docker + Docker Compose.

Observability (Требование для диплома):

Метрики: Prometheus.

Дашборды: Grafana.

Health Checks: Spring Boot Actuator (/actuator/health).

4. Microservices Architecture & Database Schema
Каждый сервис изолирован и владеет собственной схемой данных в едином кластере PostgreSQL.

4.1. User Service (user_schema)
Отвечает за аутентификацию, хранение профилей и RBAC.

users: id (UUID), email, password_hash, role, created_at.

profiles: user_id, username, avatar_url, psychological_profile (JSON), age, is_verified_author.

4.2. Content Service (content_schema)
Управление постами и лентой.

posts: id, author_id (ref User Service), title, content, published_at, likes_count.

tags: id, name.

post_tags: post_id, tag_id.

4.3. Chat Service (chat_schema)
Управление состоянием комнат и историей сообщений. Активно использует Redis для хранения процесса печати и передачи хода.

rooms: id, type (SOLO, COUPLE), status (ACTIVE, ARCHIVED), created_at.

room_participants: room_id, user_id.

messages: id, room_id, sender_id (NULL for AI), content, ai_analysis (JSON), created_at, is_locked (boolean).

4.4. AI Therapy Service
Изолированный сервис без собственной постоянной БД.

Слушает события из RabbitMQ.

Формирует промпты и взаимодействует с OpenAI API / Anthropic API.

Возвращает готовый анализ обратно в брокер сообщений.

4.5. Billing Service (billing_schema)
Управление подписками (мокирование платежей). Выступает оркестратором при покупке.

subscriptions: id, user_id, tier, expires_at.

5. System Communications & Consistency
API Composition: Используется на уровне API Gateway для сборки данных (например, запрос ленты из Content Service + обогащение именами авторов из User Service перед отправкой на клиент).

Saga Pattern (Оркестрация): Применяется для распределенных транзакций. Пример (Покупка подписки): Billing Service обновляет статус платежа -> отправляет событие -> User Service обновляет роль -> Billing Service завершает транзакцию или инициирует компенсирующую транзакцию (откат) при ошибке.

Transactional Outbox: Гарантия доставки сообщений в RabbitMQ при сбоях базы данных.

6. Core User Flows
User Flow: Landing -> Registration/Login -> Profile Setup (psychological profile, avatar) -> Subscription Purchase -> Access to AI Chats -> Creation of Couple/Solo sessions -> Browsing Content Feed -> Viewing Author Profiles.

Author Flow: Landing -> Registration/Login -> Application for Author Status -> (Approval) -> Access to Author Dashboard -> Writing/Publishing Posts -> Engaging in AI Chats.

Turn-Based Chat Flow: User 1 types multiple messages (saved in Redis/DB as drafts) -> Clicks "Pass Turn" -> Chat Service locks User 1 input -> Emits TurnCompleted event to RabbitMQ -> AI Service processes context -> Emits AiAnalysisReady -> Gateway streams response via WebSocket -> User 2 input unlocks.