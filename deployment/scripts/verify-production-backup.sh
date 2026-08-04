#!/usr/bin/env bash
set -Eeuo pipefail

backup_dir="${1:?Usage: verify-production-backup.sh <backup-directory>}"

test -f "$backup_dir/mysql.sql.gz"
test -f "$backup_dir/media-data.tar.gz"
test -f "$backup_dir/SHA256SUMS.txt"

(cd "$backup_dir" && sha256sum -c SHA256SUMS.txt)
gzip -t "$backup_dir/mysql.sql.gz"
tar -tzf "$backup_dir/media-data.tar.gz" >/dev/null

echo "Backup integrity checks passed: $backup_dir"
echo "This verifies archive readability only. Perform a separate isolated MySQL/media restore drill at least quarterly."
