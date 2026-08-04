-- Async resume analysis, follow-up generation and report generation for free interviews.
SET @has_submission_key = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'free_interview_turn' AND COLUMN_NAME = 'submission_key'
);
SET @sql = IF(@has_submission_key = 0,
  'ALTER TABLE `free_interview_turn` ADD COLUMN `submission_key` VARCHAR(64) DEFAULT NULL AFTER `turn_no`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_submission_index = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'free_interview_turn' AND INDEX_NAME = 'uk_free_interview_submission'
);
SET @sql = IF(@has_submission_index = 0,
  'ALTER TABLE `free_interview_turn` ADD UNIQUE KEY `uk_free_interview_submission` (`session_id`, `submission_key`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_status_check = (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'free_interview_session'
    AND CONSTRAINT_NAME = 'chk_free_interview_status' AND CONSTRAINT_TYPE = 'CHECK'
);
SET @sql = IF(@has_status_check > 0,
  'ALTER TABLE `free_interview_session` DROP CHECK `chk_free_interview_status`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE `free_interview_session`
  ADD CONSTRAINT `chk_free_interview_status`
  CHECK (`status` IN ('ANALYZING', 'INTERVIEWING', 'REPORT_GENERATING', 'REPORT_READY', 'FAILED'));
