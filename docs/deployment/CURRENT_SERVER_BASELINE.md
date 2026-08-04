# Current Server Release Baseline

## Baseline Marker

- Baseline ID: `SERVER-BASELINE-20260729-V16-HTTPS1`
- Effective date: `2026-07-29`
- Status: active and externally verified
- Source working directory: `D:\Ainterview`
- Source branch: `test`
- Git tag: `SERVER-BASELINE-20260729-V16-HTTPS1`
- Server root: `/opt/ai-interview-platform`
- OpenTalking root: `/opt/digital_human/opentalking`

All future release packages, migration instructions, and server update steps
must be calculated from this baseline. Do not repeat updates that are already
listed below.

## Included In This Baseline

- The platform full release uploaded on 2026-07-29.
- The report-end hotfix and Flyway migration V16.
- Flyway schema history is successfully applied through V16.
- Prompt version management and AI generation audit are active.
- Free resume interview, resume analysis, ten-round follow-up, history, resume,
  and report workflows are included.
- Choice-question scoring and insufficient-question report warnings are
  included.
- Candidate notification lookup and registration verification improvements are
  included.
- OpenTalking settings have been synchronized to the server.
- The OpenTalking read-only speech patch is installed.
- OpenTalking API uses port 8210 and the web app uses port 5280.
- Server STUN/TURN and relay policy remain server-specific and must not be
  overwritten by portable settings.
- `https://ainterview.xyz` uses the certificate stored under the platform
  `ssl` directory.
- HTTPS Nginx configuration is persisted outside the frontend container.
- Public HTTPS verification returned HTTP 200 with a valid certificate on the
  effective date.

The migrated repository does not carry the one-off baseline changelog. Use the
continuous [`docs/CHANGELOG.md`](../CHANGELOG.md) and this server marker as the
handoff record. The legacy branch and tag names below describe server history;
they are not automatically recreated in a new Git repository.

## Server-Persistent Files

The following paths are production configuration, not ordinary release files.
Never overwrite or delete them during an application update:

```text
/opt/ai-interview-platform/.env
/opt/ai-interview-platform/ssl/
/opt/ai-interview-platform/nginx/default.conf.template
/opt/digital_human/opentalking/.env
```

The repository copy at
`deployment/nginx/default.conf.template` is a recovery reference. The server
copy remains authoritative and must be backed up before every update.

The production Compose configuration must retain:

```yaml
ports:
  - "${APP_PORT:-80}:80"
  - "443:443"

volumes:
  - ./ssl:/etc/nginx/ssl:ro
  - ./nginx/default.conf.template:/etc/nginx/templates/default.conf.template:ro
```

## Rules For The Next Update

1. The next database migration version is V17 or later. Do not recreate or
   manually re-run V1 through V16.
2. Compare a packaged Compose file with the server Compose file before merging.
   Never copy it over the server file blindly.
3. Back up `.env`, Compose, `ssl`, and `nginx` before recreating containers.
4. Run `docker compose run --rm --no-deps frontend nginx -t` before recreating
   the frontend.
5. Never run `docker compose down -v` on the production server.
6. Synchronize only the approved portable OpenTalking keys. Preserve server
   ports, TURN credentials, ICE policy, and public network settings.
7. Build future release notes as changes after
   `SERVER-BASELINE-20260729-V16-HTTPS1`.

## Required Post-Update Checks

```bash
docker compose ps
docker compose logs --tail=100 backend
docker compose exec -T frontend nginx -T 2>&1 | \
  grep -nE 'listen|ssl_certificate'
curl -I http://127.0.0.1/
curl -kI --resolve ainterview.xyz:443:127.0.0.1 \
  https://ainterview.xyz/
curl -s http://127.0.0.1/opentalking/models
```

The frontend check must still show both `listen 80` and `listen 443 ssl`.

## Handoff Statement

Use this statement when another AI prepares an update:

> The production server is already at
> `SERVER-BASELINE-20260729-V16-HTTPS1`. Prepare and deploy only changes after
> this baseline. Start new Flyway migrations at V17, preserve all
> server-persistent files and HTTPS mounts, and do not repeat previous full
> release, V9-V16, OpenTalking synchronization, or read-only speech patch
> steps unless verification proves one is missing.
