# Deployment Guide

This guide explains how to deploy the entire Diplom microservice application onto a single production server using Docker Compose.

## Prerequisites
- A Linux server (e.g., Ubuntu) with **Docker** and **Docker Compose** installed.
- (Optional but recommended) A registered domain name pointing to your server's IP address.

## 1. Setup Environment Configuration

1. Clone or copy the repository onto the server.
2. Copy the example environment file:
   ```bash
   cp .env.example .env
   ```
3. Edit the `.env` file with your real secrets:
   - `DOMAIN`: Set to your real domain (e.g., `example.com`) to automatically obtain Let's Encrypt HTTPS certificates. For local testing without a domain, leave it as `:80`.
   - `POSTGRES_PASSWORD`: Use a strong, secure password.
   - `JWT_SECRET`: Provide a secure HS256 JWT secret.
   - `INTERNAL_API_KEY`: A secure key for internal microservice communication.
   - `LLM_API_KEY`: Your real LLM API key for the AI chat features (OpenRouter by default; see `.env.example`).
   - `EMBEDDINGS_*` / `DIARY_LLM_MODEL`: diary memory settings (optional; defaults reuse `LLM_*`). `EMBEDDINGS_DIMS` is written into the database schema on first start and must not be changed afterwards.

### Upgrading an existing deployment to the diary release (pgvector)

The diary's semantic memory needs the `vector` extension, so the Postgres image changes from
`postgres:16-alpine` to `pgvector/pgvector:pg16`. The data volume is reused as is (same major
version), but the new image is glibc-based while the old one was musl-based, so Postgres will warn
about a collation version mismatch. After the first start on the new image run once:
```bash
docker compose -f docker-compose.prod.yml exec postgres psql -U admin -d diplom_db \
  -c "ALTER DATABASE diplom_db REFRESH COLLATION VERSION;" -c "REINDEX DATABASE diplom_db;"
```
For a local `infrastructure/docker-compose.yml` setup it is simpler to recreate the volume
(`docker compose down -v`). The `V6__diary` migration creates the extension itself (`admin` is a superuser).

## 2. Build and Start the Services

Start all services in detached mode. This command will build all backend jars, the frontend static assets, and start the infrastructure containers:
```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Wait a few minutes for the initial build to complete. The API gateway, backend services, and web proxy will come online one by one as they pass their healthchecks.

## 3. Verify the Deployment

You can check the overall health of the cluster:
```bash
docker compose -f docker-compose.prod.yml ps
```

To view logs from all services:
```bash
docker compose -f docker-compose.prod.yml logs -f
```
Or for a specific service:
```bash
docker compose -f docker-compose.prod.yml logs -f web
docker compose -f docker-compose.prod.yml logs -f api-gateway
```

### Verification Checklist (Local / `DOMAIN=:80`)
- [ ] Open `http://localhost` in your browser. The frontend should load successfully.
- [ ] Register a new account and log in. The REST API is successfully proxying through Caddy to the Gateway.
- [ ] View the content feed; it should show content locked behind a paywall for FREE users.
- [ ] Start a Solo Chat session and send a message. If the AI replies, the WebSocket connection (`/ws`) and LLM API are configured correctly.
- [ ] Purchase a subscription (this uses the stub MVP payment system which runs by default on the `dev` profile). The content should unlock.
- [ ] Open `/diary`, write a few entries for today and press "Подвести итог дня". Within a minute the
  day shows a summary; the "Что собеседник помнит о вас" panel on `/diary` lists extracted facts. Write on the next day: the AI
  refers to the previous day (the prompt contains the previous day's summary and RAG quotes).

### Diary pipeline: automated test and manual checks

The whole diary memory chain (day summary → facts → RAG → ISO-week summary → month summary → "a year
ago") is covered by `DiaryPipelineIntegrationTest`. It needs a running Docker daemon (Docker Desktop
is enough) and is skipped silently otherwise:
```bash
mvn -f backend/pom.xml -pl chat-service test -Dtest=DiaryPipelineIntegrationTest
```
The test starts throwaway `pgvector/pgvector:pg16`, `redis:7-alpine` and `rabbitmq:3-alpine`
containers, runs the real Flyway migrations, replaces only the LLM/embeddings clients and the clock,
and asserts what the assembled prompt contains at each step. No API key is needed.

On a live stand the sweep waits for calendar conditions (day closes at `utc_today − 2`, periods at
`period_end + 3`). To exercise the same steps immediately, use the ADMIN-only operator endpoints
through the gateway with an admin JWT (`USER_ID` is the author's uuid, dates are ISO):
```bash
curl -X POST -H "Authorization: Bearer $JWT" http://localhost/internal/v1/admin/diary/sweep
curl -X POST -H "Authorization: Bearer $JWT" http://localhost/internal/v1/admin/diary/users/$USER_ID/days/2026-09-17/close
curl -X POST -H "Authorization: Bearer $JWT" http://localhost/internal/v1/admin/diary/users/$USER_ID/periods/WEEK/2026-09-14/generate
curl -X POST -H "Authorization: Bearer $JWT" http://localhost/internal/v1/admin/diary/users/$USER_ID/periods/MONTH/2026-09-01/generate
```
`sweep` returns the counts of closed days, caught-up summaries, indexed turns and generated period
summaries; `generate` answers 422 while the period has no closed days with summaries. Day summaries,
facts and indexing run asynchronously after a close — allow a few seconds and check
`docker compose -f docker-compose.prod.yml logs chat-service | grep -i diary`.

## Data Persistence
PostgreSQL, Redis, RabbitMQ, and Caddy store their data in named Docker volumes. If you ever need to stop the services or restart the host, your data will persist automatically.
```bash
# To stop safely without losing data
docker compose -f docker-compose.prod.yml down
```
