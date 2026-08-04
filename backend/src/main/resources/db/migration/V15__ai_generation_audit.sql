CREATE TABLE IF NOT EXISTS `ai_generation_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_id` VARCHAR(64) NOT NULL,
  `task_id` BIGINT UNSIGNED DEFAULT NULL,
  `interview_id` BIGINT UNSIGNED DEFAULT NULL,
  `free_interview_session_id` BIGINT UNSIGNED DEFAULT NULL,
  `generation_type` VARCHAR(40) NOT NULL,
  `prompt_code` VARCHAR(64) DEFAULT NULL,
  `prompt_version_no` INT UNSIGNED DEFAULT NULL,
  `provider` VARCHAR(32) NOT NULL,
  `model` VARCHAR(128) NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `latency_ms` BIGINT UNSIGNED DEFAULT NULL,
  `input_chars` INT UNSIGNED NOT NULL DEFAULT 0,
  `output_chars` INT UNSIGNED NOT NULL DEFAULT 0,
  `prompt_tokens` INT UNSIGNED DEFAULT NULL,
  `completion_tokens` INT UNSIGNED DEFAULT NULL,
  `total_tokens` INT UNSIGNED DEFAULT NULL,
  `http_status` INT DEFAULT NULL,
  `error_type` VARCHAR(128) DEFAULT NULL,
  `error_message` VARCHAR(1000) DEFAULT NULL,
  `created_by` BIGINT UNSIGNED DEFAULT NULL,
  `started_at` DATETIME(3) NOT NULL,
  `finished_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_generation_request_id` (`request_id`),
  KEY `idx_ai_generation_task` (`task_id`),
  KEY `idx_ai_generation_interview` (`interview_id`),
  KEY `idx_ai_generation_free_session` (`free_interview_session_id`),
  KEY `idx_ai_generation_status_created` (`status`, `created_at`),
  KEY `idx_ai_generation_prompt` (`prompt_code`, `prompt_version_no`),
  CONSTRAINT `fk_ai_generation_task` FOREIGN KEY (`task_id`) REFERENCES `ai_task` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ai_generation_interview` FOREIGN KEY (`interview_id`) REFERENCES `interview` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ai_generation_free_session` FOREIGN KEY (`free_interview_session_id`) REFERENCES `free_interview_session` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ai_generation_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_ai_generation_status` CHECK (`status` IN ('RUNNING', 'SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Auditable AI model generation calls';

ALTER TABLE `report`
  ADD COLUMN `scoring_prompt_code` VARCHAR(64) DEFAULT NULL AFTER `generation_method`,
  ADD COLUMN `scoring_prompt_version_no` INT UNSIGNED DEFAULT NULL AFTER `scoring_prompt_code`,
  ADD COLUMN `report_prompt_code` VARCHAR(64) DEFAULT NULL AFTER `scoring_prompt_version_no`,
  ADD COLUMN `report_prompt_version_no` INT UNSIGNED DEFAULT NULL AFTER `report_prompt_code`;

ALTER TABLE `ai_prompt_version`
  ADD COLUMN `active_prompt_code` VARCHAR(64)
    GENERATED ALWAYS AS (CASE WHEN `is_active` = 1 THEN `prompt_code` ELSE NULL END) STORED,
  ADD UNIQUE KEY `uk_ai_prompt_single_active` (`active_prompt_code`);
