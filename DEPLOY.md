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
   - `LLM_API_KEY`: Your real LLM API key for the AI chat features.

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

## Data Persistence
PostgreSQL, Redis, RabbitMQ, and Caddy store their data in named Docker volumes. If you ever need to stop the services or restart the host, your data will persist automatically.
```bash
# To stop safely without losing data
docker compose -f docker-compose.prod.yml down
```
