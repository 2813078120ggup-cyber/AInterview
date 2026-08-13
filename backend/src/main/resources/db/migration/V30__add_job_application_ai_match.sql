-- JD-driven AI matching for recruitment applications.
-- V27-V29 are published and must remain unchanged.

ALTER TABLE `job_application`
  ADD COLUMN `match_status` VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED/MANUAL' AFTER `match_details`,
  ADD COLUMN `match_version` INT NOT NULL DEFAULT 0 COMMENT 'Resume parse version used for matching' AFTER `match_status`,
  ADD COLUMN `match_error` VARCHAR(1000) DEFAULT NULL AFTER `match_version`,
  ADD COLUMN `match_started_at` DATETIME DEFAULT NULL AFTER `match_error`,
  ADD COLUMN `match_completed_at` DATETIME DEFAULT NULL AFTER `match_started_at`,
  ADD KEY `idx_job_application_match_status` (`match_status`, `updated_at`),
  ADD CONSTRAINT `chk_job_application_match_status` CHECK (`match_status` IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'MANUAL'));

UPDATE `job_application`
SET `match_status` = 'MANUAL'
WHERE `match_score` IS NOT NULL AND `match_status` = 'MANUAL';
