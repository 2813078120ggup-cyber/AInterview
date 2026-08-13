-- Candidate account and session security foundation.
-- Do not backfill historical contact verification timestamps or notification preferences.

ALTER TABLE `user`
  ADD COLUMN `avatar_media_id` BIGINT UNSIGNED DEFAULT NULL AFTER `avatar_url`,
  ADD COLUMN `email_verified_at` DATETIME DEFAULT NULL AFTER `email`,
  ADD COLUMN `phone_verified_at` DATETIME DEFAULT NULL AFTER `phone`,
  ADD COLUMN `security_version` INT NOT NULL DEFAULT 0 AFTER `status`,
  ADD COLUMN `version` INT NOT NULL DEFAULT 0 AFTER `security_version`,
  ADD KEY `idx_user_avatar_media` (`avatar_media_id`),
  ADD CONSTRAINT `fk_user_avatar_media` FOREIGN KEY (`avatar_media_id`) REFERENCES `media_file` (`id`) ON DELETE SET NULL;

ALTER TABLE `refresh_token`
  ADD COLUMN `session_id` CHAR(36) DEFAULT NULL AFTER `user_id`,
  ADD COLUMN `last_used_at` DATETIME DEFAULT NULL AFTER `revoked_at`,
  ADD COLUMN `revoked_reason` VARCHAR(64) DEFAULT NULL AFTER `last_used_at`;

-- Every historical token is treated as its own session. This intentionally does
-- not infer a device from IP, user-agent, or creation time.
UPDATE `refresh_token`
SET `session_id` = UUID()
WHERE `session_id` IS NULL;

ALTER TABLE `refresh_token`
  MODIFY COLUMN `session_id` CHAR(36) NOT NULL,
  ADD KEY `idx_refresh_token_user_session` (`user_id`, `session_id`, `created_at`, `id`),
  ADD KEY `idx_refresh_token_session_state` (`session_id`, `revoked_at`, `expires_at`);

CREATE TABLE IF NOT EXISTS `user_notification_preference` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `site_enabled` TINYINT NOT NULL DEFAULT 1,
  `email_enabled` TINYINT NOT NULL DEFAULT 0,
  `sms_enabled` TINYINT NOT NULL DEFAULT 0,
  `version` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_notification_preference_user_event` (`user_id`, `event_type`),
  KEY `idx_user_notification_preference_user_updated` (`user_id`, `updated_at`, `id`),
  CONSTRAINT `fk_user_notification_preference_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_user_notification_preference_site_enabled` CHECK (`site_enabled` IN (0, 1)),
  CONSTRAINT `chk_user_notification_preference_email_enabled` CHECK (`email_enabled` IN (0, 1)),
  CONSTRAINT `chk_user_notification_preference_sms_enabled` CHECK (`sms_enabled` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Per-user notification preferences';
