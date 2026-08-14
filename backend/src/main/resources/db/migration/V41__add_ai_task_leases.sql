-- Recoverable leases for asynchronous AI tasks. Existing RUNNING rows become immediately reclaimable.

ALTER TABLE `ai_task`
  ADD COLUMN `claim_token` CHAR(36) DEFAULT NULL AFTER `started_at`,
  ADD COLUMN `locked_by` VARCHAR(128) DEFAULT NULL AFTER `claim_token`,
  ADD COLUMN `lease_expires_at` DATETIME DEFAULT NULL AFTER `locked_by`,
  ADD COLUMN `heartbeat_at` DATETIME DEFAULT NULL AFTER `lease_expires_at`,
  ADD KEY `idx_ai_task_running_lease` (`status`, `lease_expires_at`, `id`);

UPDATE `ai_task`
SET `lease_expires_at` = CURRENT_TIMESTAMP
WHERE `status` = 'RUNNING' AND `lease_expires_at` IS NULL;
