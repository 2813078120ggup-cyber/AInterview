#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

project_root="${PROJECT_ROOT:-/opt/ai-interview-platform}"
backup_root="${BACKUP_ROOT:-$project_root/backups}"
stamp="$(date +%Y%m%d-%H%M%S)"
backup_dir="$backup_root/automatic-$stamp"
media_volume="${MEDIA_VOLUME:-${COMPOSE_PROJECT_NAME:-ai-interview-platform}_media_data}"

cd "$project_root"
mkdir -p "$backup_dir"

docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers --events "$MYSQL_DATABASE"' \
  | gzip -9 > "$backup_dir/mysql.sql.gz"

docker run --rm \
  --volume "$media_volume:/source:ro" \
  --volume "$backup_dir:/backup" \
  alpine:3.20 sh -c 'tar -czf /backup/media-data.tar.gz -C /source .'

sha256sum "$backup_dir/mysql.sql.gz" "$backup_dir/media-data.tar.gz" > "$backup_dir/SHA256SUMS.txt"
{
  echo "created_at=$(date --iso-8601=seconds)"
  echo "compose_project=${COMPOSE_PROJECT_NAME:-ai-interview-platform}"
  echo "media_volume=$media_volume"
  docker compose ps --format json
} > "$backup_dir/manifest.txt"

echo "Backup created: $backup_dir"
echo "Verify with deployment/scripts/verify-production-backup.sh $backup_dir"
echo "Upload this directory to encrypted, access-controlled object storage before considering the backup complete."
