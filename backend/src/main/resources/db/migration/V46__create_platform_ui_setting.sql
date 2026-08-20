-- Globally controlled UI behavior.  Keep this table intentionally narrow: the
-- public read endpoint exposes only the non-sensitive, presentation setting.
CREATE TABLE IF NOT EXISTS `platform_ui_setting` (
  `id` BIGINT UNSIGNED NOT NULL,
  `mouse_follower_enabled` TINYINT NOT NULL DEFAULT 1,
  `updated_by` BIGINT UNSIGNED DEFAULT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_platform_ui_setting_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_platform_ui_setting_singleton` CHECK (`id` = 1),
  CONSTRAINT `chk_platform_ui_setting_mouse_follower` CHECK (`mouse_follower_enabled` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Global presentation settings for the platform';

INSERT INTO `platform_ui_setting` (`id`, `mouse_follower_enabled`)
VALUES (1, 1)
ON DUPLICATE KEY UPDATE `id` = `id`;
