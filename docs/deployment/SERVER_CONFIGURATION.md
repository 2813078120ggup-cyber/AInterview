# Server Configuration Reference

This document records the current deployment layout for the AI Interview
Platform and OpenTalking virtual human service. Keep credentials only in server
`.env` files; do not add keys, passwords, or TURN credentials to this document.

## Server

- Provider: Alibaba Cloud ECS
- Operating system: Alibaba Cloud Linux 3
- Public address: `47.93.204.227`
- Private address: `172.25.32.145`
- Time zone: `Asia/Shanghai`

## AI Interview Platform

### Paths

| Purpose | Path |
| --- | --- |
| Deployment root | `/opt/ai-interview-platform` |
| Docker Compose file | `/opt/ai-interview-platform/docker-compose.yml` |
| Server secrets | `/opt/ai-interview-platform/.env` |
| Fresh database initialization | `/opt/ai-interview-platform/docs/database/docker-init/01-ai-interview-init.sql` |
| Update backups | `/opt/ai-interview-platform/backups/` |
| Uploaded release archives | `/home/admin/ai-upload/` |

### Services

| Service | Container role | Current image/runtime |
| --- | --- | --- |
| `frontend` | Nginx web UI and `/api` reverse proxy | Nginx `1.27.5` |
| `backend` | Spring Boot API | Java `17.0.19`, Spring Boot `3.3.2` |
| `mysql` | Application database | MySQL `8.0.36` |
| `redis` | Cache and task support | Redis `7.2-alpine` |

The public site is exposed on TCP `80`. The backend listens on container port
`8080` with Spring context path `/api`; it is accessed through the frontend
reverse proxy as `/api/v1/...`.

### Important Environment Variable Names

The following must be set in `/opt/ai-interview-platform/.env` and must never
be committed or sent in an update archive:

```text
MYSQL_ROOT_PASSWORD
MYSQL_APP_USER
MYSQL_APP_PASSWORD
REDIS_PASSWORD
JWT_SECRET
DEEPSEEK_ENABLED
DEEPSEEK_API_KEY
DEEPSEEK_BASE_URL
DEEPSEEK_MODEL
APP_PORT
OPENTALKING_UPSTREAM
```

### Routine Commands

```bash
cd /opt/ai-interview-platform
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend
docker compose logs --tail=100 mysql
```

## OpenTalking Virtual Human

### Paths and Start Command

| Purpose | Path or command |
| --- | --- |
| OpenTalking source | `/opt/digital_human/opentalking` |
| Python virtual environment | `/opt/digital_human/opentalking/.venv` |
| OpenTalking environment file | `/opt/digital_human/opentalking/.env` |
| Unified API executable | `/opt/digital_human/opentalking/.venv/bin/opentalking-unified` |
| Start log | `/opt/digital_human/opentalking/logs/unified.log` |

```bash
cd /opt/digital_human/opentalking
nohup bash scripts/start_unified.sh --env .env --mock --api-port 8210 --web-port 5280 \
  > logs/unified.log 2>&1 &
```

### Ports

| Component | Address |
| --- | --- |
| OpenTalking API | `http://127.0.0.1:8210` |
| OpenTalking API docs | `http://127.0.0.1:8210/docs` |
| OpenTalking web UI | `http://127.0.0.1:5280` |
| TURN | UDP `3478` and UDP `30000-60999` |

The frontend reaches OpenTalking through its configured reverse proxy. Keep the
OpenTalking API port and frontend proxy target aligned.

The browser-safe provider endpoint stored in `ai_provider_config.base_url` is
`/opentalking`. Docker defaults to
`OPENTALKING_UPSTREAM=host.docker.internal:8210`; local Docker testing may
override it with `host.docker.internal:8000`.

### OpenTalking Configuration Areas

| Function | Location |
| --- | --- |
| LLM provider, model, and system prompt | `/opt/digital_human/opentalking/.env` |
| Portable settings merge tool | `/opt/ai-interview-platform/opentalking/sync_opentalking_settings.py` |
| TTS/STT provider and voice | `/opt/digital_human/opentalking/.env` |
| WebRTC STUN/TURN settings | `/opt/digital_human/opentalking/.env` |
| TURN server configuration | `/etc/coturn/turnserver.conf` |
| Read-only/re-read behavior | AI Interview frontend plus the OpenTalking `synthesis_runner.py` patch |

After changing OpenTalking `.env`, restart the unified service. Confirm its
active configuration with:

```bash
curl -s http://127.0.0.1:8210/runtime-config
curl -s http://127.0.0.1:8210/sessions/webrtc/ice-config
```

## Capture Exact Runtime Versions

Run these on the server after major changes and append the output to a dated
operations note:

```bash
docker --version
docker compose version

cd /opt/ai-interview-platform
docker compose images
docker compose exec -T backend java -version
docker compose exec -T mysql mysql --version
docker compose exec -T redis redis-server --version

/opt/digital_human/opentalking/.venv/bin/python --version
/opt/digital_human/opentalking/.venv/bin/opentalking-unified --help | head -1
```

## Database Safety

- `01-ai-interview-init.sql` runs only when MySQL starts with an empty volume.
- Flyway runs from the backend JAR before the application starts. Legacy
  non-empty databases are baselined at V8 and automatically receive V9 and all
  later unapplied migrations.
- Applied versions and checksums are recorded in `flyway_schema_history`.
- Never modify an already deployed `V*.sql`; add a new migration instead.
- AI prompt content and activation history are stored in `ai_prompt_version`
  and `ai_prompt_activation_log`; `uk_ai_prompt_single_active` enforces one
  active version per prompt code.
- AI provider calls are recorded in `ai_generation_record` without prompt,
  resume, answer, or generated-response bodies. Reports store the exact scoring
  and report prompt versions used for generation.
- Free interviews use the shared `ai_task` queue. The default poll interval is
  `1000 ms`, configurable with `AI_TASK_POLL_INTERVAL_MS`.
- Back up MySQL before schema or data migrations.
- Do not use `docker compose down -v` unless intentionally rebuilding all
  persistent data from scratch.
