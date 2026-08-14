ALTER TABLE `ai_provider_config`
  ADD COLUMN `last_test_state` VARCHAR(16) NULL AFTER `remark`,
  ADD COLUMN `last_test_status_code` INT NULL AFTER `last_test_state`,
  ADD COLUMN `last_test_latency_ms` BIGINT UNSIGNED NULL AFTER `last_test_status_code`,
  ADD COLUMN `last_test_message` VARCHAR(255) NULL AFTER `last_test_latency_ms`,
  ADD COLUMN `last_tested_at` DATETIME NULL AFTER `last_test_message`;
