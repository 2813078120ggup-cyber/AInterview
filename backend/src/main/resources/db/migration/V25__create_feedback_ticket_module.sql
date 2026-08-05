-- Persistent feedback tickets, immutable activity timeline, ticket attachments,
-- per-user site notifications, and per-ticket read cursors.

CREATE TABLE IF NOT EXISTS `feedback_ticket` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `ticket_no` VARCHAR(32) NOT NULL,
  `creator_id` BIGINT UNSIGNED NOT NULL,
  `ticket_type` VARCHAR(32) NOT NULL,
  `title` VARCHAR(120) NOT NULL,
  `description` TEXT NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  `assignee_id` BIGINT UNSIGNED DEFAULT NULL,
  `resolution` VARCHAR(2000) DEFAULT NULL,
  `version` INT NOT NULL DEFAULT 0,
  `submitted_at` DATETIME DEFAULT NULL,
  `processing_at` DATETIME DEFAULT NULL,
  `resolved_at` DATETIME DEFAULT NULL,
  `closed_at` DATETIME DEFAULT NULL,
  `last_activity_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_ticket_no` (`ticket_no`),
  KEY `idx_feedback_ticket_creator_status_updated` (`creator_id`, `status`, `updated_at`),
  KEY `idx_feedback_ticket_assignee_status_updated` (`assignee_id`, `status`, `updated_at`),
  KEY `idx_feedback_ticket_status_activity` (`status`, `last_activity_at`),
  KEY `idx_feedback_ticket_type_status` (`ticket_type`, `status`),
  CONSTRAINT `fk_feedback_ticket_creator` FOREIGN KEY (`creator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_feedback_ticket_assignee` FOREIGN KEY (`assignee_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_feedback_ticket_type` CHECK (`ticket_type` IN ('INTERVIEW_FAILURE', 'FEATURE_SUGGESTION', 'BUG_REPORT')),
  CONSTRAINT `chk_feedback_ticket_status` CHECK (`status` IN ('DRAFT', 'PENDING', 'PROCESSING', 'RESOLVED', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feedback tickets';

CREATE TABLE IF NOT EXISTS `feedback_ticket_activity` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `ticket_id` BIGINT UNSIGNED NOT NULL,
  `actor_id` BIGINT UNSIGNED DEFAULT NULL,
  `activity_type` VARCHAR(16) NOT NULL,
  `content` TEXT DEFAULT NULL,
  `from_status` VARCHAR(16) DEFAULT NULL,
  `to_status` VARCHAR(16) DEFAULT NULL,
  `from_assignee_id` BIGINT UNSIGNED DEFAULT NULL,
  `to_assignee_id` BIGINT UNSIGNED DEFAULT NULL,
  `client_request_id` VARCHAR(80) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_activity_client_request` (`ticket_id`, `client_request_id`),
  KEY `idx_feedback_activity_ticket_created` (`ticket_id`, `created_at`, `id`),
  KEY `idx_feedback_activity_actor_created` (`actor_id`, `created_at`),
  CONSTRAINT `fk_feedback_activity_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `feedback_ticket` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_feedback_activity_actor` FOREIGN KEY (`actor_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_feedback_activity_type` CHECK (`activity_type` IN ('COMMENT', 'STATUS_CHANGE', 'ASSIGNMENT', 'SUBMITTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feedback ticket timeline';

CREATE TABLE IF NOT EXISTS `feedback_ticket_attachment` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `ticket_id` BIGINT UNSIGNED NOT NULL,
  `activity_id` BIGINT UNSIGNED DEFAULT NULL,
  `media_id` BIGINT UNSIGNED NOT NULL,
  `uploader_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_attachment_media` (`media_id`),
  KEY `idx_feedback_attachment_ticket_created` (`ticket_id`, `created_at`, `id`),
  CONSTRAINT `fk_feedback_attachment_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `feedback_ticket` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_feedback_attachment_activity` FOREIGN KEY (`activity_id`) REFERENCES `feedback_ticket_activity` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_feedback_attachment_media` FOREIGN KEY (`media_id`) REFERENCES `media_file` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_feedback_attachment_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feedback ticket attachments';

CREATE TABLE IF NOT EXISTS `site_notification` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `recipient_id` BIGINT UNSIGNED NOT NULL,
  `notification_type` VARCHAR(48) NOT NULL,
  `title` VARCHAR(160) NOT NULL,
  `content` VARCHAR(2000) NOT NULL,
  `business_type` VARCHAR(48) DEFAULT NULL,
  `business_id` BIGINT UNSIGNED DEFAULT NULL,
  `dedupe_key` VARCHAR(191) DEFAULT NULL,
  `read_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_notification_recipient_dedupe` (`recipient_id`, `dedupe_key`),
  KEY `idx_site_notification_recipient_read_created` (`recipient_id`, `read_at`, `created_at`, `id`),
  KEY `idx_site_notification_business` (`business_type`, `business_id`),
  CONSTRAINT `fk_site_notification_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Persistent in-app notifications';

CREATE TABLE IF NOT EXISTS `feedback_ticket_read_state` (
  `ticket_id` BIGINT UNSIGNED NOT NULL,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `last_read_activity_id` BIGINT UNSIGNED DEFAULT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`ticket_id`, `user_id`),
  CONSTRAINT `fk_feedback_read_state_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `feedback_ticket` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_feedback_read_state_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_feedback_read_state_activity` FOREIGN KEY (`last_read_activity_id`) REFERENCES `feedback_ticket_activity` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Feedback ticket read cursors';
