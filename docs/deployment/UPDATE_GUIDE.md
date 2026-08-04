# AI Interview Platform Update Guide

This guide updates the Alibaba Cloud Docker deployment without removing the
existing MySQL, Redis, or media volumes.

## Current Production Baseline

The current production marker is
`SERVER-BASELINE-20260729-V16-HTTPS1`. Read
[`CURRENT_SERVER_BASELINE.md`](CURRENT_SERVER_BASELINE.md) before preparing any
release. All future packages and instructions must contain only changes after
that marker.

## Deployment Paths

- Local release directory: `D:\ai-interview-release`
- Server deployment directory: `/opt/ai-interview-platform`
- Server OpenTalking directory: `/opt/digital_human/opentalking`

Never include a real `.env` in an update archive. It contains secrets and must
remain on the server during application updates.

## 1. Release Contents

The archive must contain:

- `docker-compose.yml` and `.env.example`
- `docs/database/docker-init/01-ai-interview-init.sql` for a new empty MySQL volume only
- the backend image containing executable Flyway migrations V1 and V9–V24;
  do not package retired manual migration copies
- `docs/`
- `docs/deployment/patches/opentalking-readonly-speak.patch`
- `deployment/opentalking/opentalking-prompt.env.example` without secrets
- `deployment/opentalking/sync_opentalking_settings.py` and its README
- `ai-interview-platform-images.tar`
- `SHA256SUMS.txt`

Keep these repository-relative paths unchanged in the release archive so the
Compose mounts and all update commands continue to resolve correctly.

## 2. Upload

Upload the archive with Alibaba Cloud Workbench to `/home/admin/ai-upload`,
then run:

```bash
sudo -i
mv /home/admin/ai-upload/<release-archive>.tar.gz /opt/ai-interview-platform/
cd /opt/ai-interview-platform
```

## 3. Back Up Files and MySQL

```bash
stamp=$(date +%Y%m%d-%H%M%S)
backup="backups/pre-update-$stamp"
stage="/tmp/ai-interview-update-$stamp"

mkdir -p "$backup" "$stage"
cp -a docker-compose.yml "$backup/"
[ -f .env ] && cp -a .env "$backup/"
[ -d database ] && cp -a database "$backup/"
[ -d ssl ] && cp -a ssl "$backup/"
[ -d nginx ] && cp -a nginx "$backup/"

docker compose exec -T mysql sh -c \
'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
> "$backup/mysql-before-update.sql"

tar -xzf <release-archive>.tar.gz -C "$stage"
(cd "$stage" && sha256sum -c SHA256SUMS.txt)
```

## 4. Merge Deployment Files and Load Images

```bash
mkdir -p docs deployment/opentalking
cp -af "$stage/docs/." ./docs/
cp -af "$stage/deployment/opentalking/." ./deployment/opentalking/

# Review Compose changes before merging. The server copy contains persistent
# HTTPS ports and mounts that a release package must never remove.
diff -u docker-compose.yml "$stage/docker-compose.yml" || true

# Keep the existing .env. Add this only when it is absent.
grep -q '^OPENTALKING_UPSTREAM=' .env || \
  echo 'OPENTALKING_UPSTREAM=host.docker.internal:8210' >> .env

docker compose config --quiet
docker load -i "$stage/ai-interview-platform-images.tar"
```

Merge only the required service/image/environment changes into the server
`docker-compose.yml`. After the merge, the frontend service must still contain:

```yaml
ports:
  - "${APP_PORT:-80}:80"
  - "443:443"

volumes:
  - ./ssl:/etc/nginx/ssl:ro
  - ./nginx/default.conf.template:/etc/nginx/templates/default.conf.template:ro
```

## 5. Database Migrations Are Automatic

Never run `docs/database/docker-init/01-ai-interview-init.sql` against an existing production
database. Do not manually execute V9 or later migrations during a normal
update. Flyway is packaged in the backend and runs before Spring Boot accepts
traffic.

On the first Flyway-enabled startup, a non-empty legacy database is recorded at
baseline V8, then V9 and every later unapplied migration are executed in order.
An empty database runs the V1 baseline schema first. Every later startup checks
the checksums in `flyway_schema_history` and only applies new versions.

If Flyway reports a checksum or migration error, stop the update and inspect the
backend log. Do not edit an already released migration file and do not use
`flyway repair` without first identifying why the checksum changed.

## 6. Apply the OpenTalking Read-Only Patch

This patch makes `【只朗读模式】` bypass Qwen, memory, and Agent generation.
Apply it once:

```bash
cd /opt/digital_human/opentalking
patch_file=/opt/ai-interview-platform/docs/deployment/patches/opentalking-readonly-speak.patch

if grep -q 'Read-only speech bypassed LLM' opentalking/pipeline/speak/synthesis_runner.py; then
  echo 'OpenTalking read-only patch is already installed.'
else
  cp -a opentalking/pipeline/speak/synthesis_runner.py \
    "opentalking/pipeline/speak/synthesis_runner.py.bak-$(date +%Y%m%d-%H%M%S)"
  git apply --check "$patch_file"
  git apply "$patch_file"
fi

pkill -f '/opt/digital_human/opentalking/.venv/bin/opentalking-unified' || true
nohup bash scripts/start_unified.sh --env .env --mock --api-port 8210 --web-port 5280 \
  > logs/unified.log 2>&1 &
```

The packaged prompt example is only for OpenTalking Studio. Never replace the
whole server `.env`; platform sessions use `agent_enabled=false`.

### Optional: Merge Local OpenTalking Behavior Settings

Copy the local OpenTalking `.env` from WSL to a temporary Windows file and
upload it separately. It contains secrets and must not be added to this release:

```bash
# Local WSL
cp ~/opentalking/.env /mnt/d/opentalking-local-settings.env
```

Upload `D:\opentalking-local-settings.env` to
`/home/admin/ai-upload/opentalking-local-settings.env`, then preview and apply:

```bash
cd /opt/ai-interview-platform
python=/opt/digital_human/opentalking/.venv/bin/python
tool=deployment/opentalking/sync_opentalking_settings.py
source=/home/admin/ai-upload/opentalking-local-settings.env
target=/opt/digital_human/opentalking/.env

$python $tool --source $source --target $target --dry-run
$python $tool --source $source --target $target
```

Only LLM/prompt, TTS/STT, Agent, memory, persona, and DashScope settings are
merged. Server `WEBRTC_*`, TURN, API/web ports, CORS, model paths, and runtime
directories remain unchanged. Restart OpenTalking with the command above and
verify both `/runtime-config` and `/sessions/webrtc/ice-config`.

## 7. Restart Docker Services

```bash
cd /opt/ai-interview-platform
docker compose up -d --force-recreate --no-deps backend

# Validate the effective HTTPS configuration before replacing the live
# frontend container.
docker compose run --rm --no-deps frontend nginx -t
docker compose up -d --force-recreate --no-deps frontend
docker compose ps
docker compose logs --tail=100 backend
```

The backend must stay healthy before the frontend is considered updated. In the
backend log, confirm Flyway validated the migration set and migrated the schema
successfully.

Do not run `docker compose down -v` during a normal update. It deletes the
persistent database, Redis, and media volumes.

## 8. Verify

```bash
curl -s http://127.0.0.1/opentalking/health
curl -s http://127.0.0.1/opentalking/sessions/webrtc/ice-config

curl -i -X POST http://127.0.0.1/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{}'

docker compose exec -T mysql sh -c \
'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot "$MYSQL_DATABASE" -e \
"SELECT code,base_url FROM ai_provider_config WHERE code=\"open-talking-virtual-human\";"'

docker compose exec -T mysql sh -c \
'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -e \
"SELECT installed_rank,version,description,success FROM flyway_schema_history ORDER BY installed_rank; \
 SELECT prompt_code,version_no,is_active FROM ai_prompt_version ORDER BY prompt_code,version_no; \
 SELECT COUNT(*) AS ai_generation_records FROM ai_generation_record; \
 SHOW INDEX FROM ai_prompt_version WHERE Key_name=\"uk_ai_prompt_single_active\";"'
```

The registration request should return an HTTP `400` validation response. The
provider query should return `/opentalking`. Finally test both normal simulated
interview and free resume interview in a browser.

## Rollback

Restore the timestamped `docker-compose.yml` and reload the previous image IDs.
Flyway migrations are forward-only. Restoring an older application image does
not undo schema changes; a schema rollback requires the MySQL backup from step
3 or a separately reviewed compensating migration.
