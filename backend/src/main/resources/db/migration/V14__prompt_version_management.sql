CREATE TABLE IF NOT EXISTS `ai_prompt_version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `prompt_code` VARCHAR(64) NOT NULL,
  `prompt_name` VARCHAR(128) NOT NULL,
  `category` VARCHAR(32) NOT NULL,
  `version_no` INT UNSIGNED NOT NULL,
  `system_template` LONGTEXT NOT NULL,
  `user_template` LONGTEXT NOT NULL,
  `is_active` TINYINT NOT NULL DEFAULT 0,
  `change_note` VARCHAR(500) DEFAULT NULL,
  `created_by` BIGINT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `activated_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_prompt_code_version` (`prompt_code`, `version_no`),
  KEY `idx_ai_prompt_code_active` (`prompt_code`, `is_active`),
  KEY `idx_ai_prompt_created_by` (`created_by`),
  CONSTRAINT `fk_ai_prompt_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_ai_prompt_active` CHECK (`is_active` IN (0, 1)),
  CONSTRAINT `chk_ai_prompt_version` CHECK (`version_no` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable versioned AI prompt templates';

CREATE TABLE IF NOT EXISTS `ai_prompt_activation_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `prompt_code` VARCHAR(64) NOT NULL,
  `from_version_no` INT UNSIGNED DEFAULT NULL,
  `to_version_no` INT UNSIGNED NOT NULL,
  `action` VARCHAR(16) NOT NULL,
  `note` VARCHAR(500) DEFAULT NULL,
  `operator_id` BIGINT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_prompt_log_code_created` (`prompt_code`, `created_at`),
  KEY `idx_ai_prompt_log_operator` (`operator_id`),
  CONSTRAINT `fk_ai_prompt_log_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_ai_prompt_log_action` CHECK (`action` IN ('INITIAL', 'ACTIVATE', 'ROLLBACK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Prompt activation and rollback history';
