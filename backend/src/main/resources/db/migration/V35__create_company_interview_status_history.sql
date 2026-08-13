-- Unified history for company-visible AI and offline interview activities.
-- V27-V34 are published and must remain unchanged.

CREATE TABLE IF NOT EXISTS `interview_status_history` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `interview_kind` VARCHAR(16) NOT NULL COMMENT 'AI or OFFLINE',
  `interview_id` BIGINT UNSIGNED DEFAULT NULL,
  `offline_interview_id` BIGINT UNSIGNED DEFAULT NULL,
  `application_id` BIGINT UNSIGNED NOT NULL,
  `from_status` VARCHAR(32) DEFAULT NULL,
  `to_status` VARCHAR(32) NOT NULL,
  `operator_id` BIGINT UNSIGNED NOT NULL,
  `reason` VARCHAR(1000) DEFAULT NULL,
  `notification_status` VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_interview_history_application_created` (`application_id`, `created_at`, `id`),
  KEY `idx_interview_history_ai_created` (`interview_id`, `created_at`, `id`),
  KEY `idx_interview_history_offline_created` (`offline_interview_id`, `created_at`, `id`),
  CONSTRAINT `fk_interview_history_ai` FOREIGN KEY (`interview_id`) REFERENCES `interview` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_interview_history_offline` FOREIGN KEY (`offline_interview_id`) REFERENCES `offline_interview` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_interview_history_application` FOREIGN KEY (`application_id`) REFERENCES `job_application` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_interview_history_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_interview_history_kind` CHECK (`interview_kind` IN ('AI', 'OFFLINE')),
  CONSTRAINT `chk_interview_history_reference` CHECK (
    (`interview_kind` = 'AI' AND `interview_id` IS NOT NULL AND `offline_interview_id` IS NULL)
    OR (`interview_kind` = 'OFFLINE' AND `interview_id` IS NULL AND `offline_interview_id` IS NOT NULL)
  ),
  CONSTRAINT `chk_interview_history_notification` CHECK (`notification_status` IN ('SENT', 'NOT_SENT', 'NOT_REQUIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Company interview activity status history';
