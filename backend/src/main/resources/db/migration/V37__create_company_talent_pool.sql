-- Company-scoped talent pool, collaboration notes and custom tags.
-- V27-V36 are published migrations and must not be modified.

CREATE TABLE IF NOT EXISTS `company_candidate` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `candidate_id` BIGINT UNSIGNED NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  `last_contacted_at` DATETIME DEFAULT NULL,
  `removed_at` DATETIME DEFAULT NULL,
  `removed_by` BIGINT UNSIGNED DEFAULT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_candidate_company_candidate` (`company_id`, `candidate_id`),
  KEY `idx_company_candidate_company_status_contact` (`company_id`, `status`, `last_contacted_at`, `updated_at`),
  KEY `idx_company_candidate_candidate` (`candidate_id`, `company_id`),
  CONSTRAINT `fk_company_candidate_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_company_candidate_candidate` FOREIGN KEY (`candidate_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_company_candidate_removed_by` FOREIGN KEY (`removed_by`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_company_candidate_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_company_candidate_status` CHECK (`status` IN ('ACTIVE', 'REMOVED')),
  CONSTRAINT `chk_company_candidate_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Company-scoped candidate talent pool membership';

CREATE TABLE IF NOT EXISTS `application_note` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `company_candidate_id` BIGINT UNSIGNED NOT NULL,
  `candidate_id` BIGINT UNSIGNED NOT NULL,
  `application_id` BIGINT UNSIGNED DEFAULT NULL,
  `author_id` BIGINT UNSIGNED NOT NULL,
  `updated_by` BIGINT UNSIGNED NOT NULL,
  `content` VARCHAR(4000) NOT NULL,
  `version` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_application_note_company_candidate_updated` (`company_id`, `company_candidate_id`, `updated_at`, `id`),
  KEY `idx_application_note_application` (`company_id`, `application_id`, `updated_at`, `id`),
  CONSTRAINT `fk_application_note_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_application_note_candidate` FOREIGN KEY (`candidate_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_application_note_pool` FOREIGN KEY (`company_candidate_id`) REFERENCES `company_candidate` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_application_note_application` FOREIGN KEY (`application_id`) REFERENCES `job_application` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_application_note_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_application_note_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_application_note_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Shared company candidate collaboration notes';

CREATE TABLE IF NOT EXISTS `company_candidate_tag` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `color` VARCHAR(32) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_candidate_tag_company_name` (`company_id`, `name`),
  KEY `idx_company_candidate_tag_company_status` (`company_id`, `status`, `name`),
  CONSTRAINT `fk_company_candidate_tag_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_company_candidate_tag_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_company_candidate_tag_status` CHECK (`status` IN (0, 1)),
  CONSTRAINT `chk_company_candidate_tag_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Company-owned candidate tags';

CREATE TABLE IF NOT EXISTS `company_candidate_tag_relation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `company_candidate_id` BIGINT UNSIGNED NOT NULL,
  `tag_id` BIGINT UNSIGNED NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_candidate_tag_relation` (`company_id`, `company_candidate_id`, `tag_id`),
  KEY `idx_company_candidate_tag_relation_candidate` (`company_id`, `company_candidate_id`, `status`),
  KEY `idx_company_candidate_tag_relation_tag` (`company_id`, `tag_id`, `status`),
  CONSTRAINT `fk_company_candidate_tag_relation_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_company_candidate_tag_relation_candidate` FOREIGN KEY (`company_candidate_id`) REFERENCES `company_candidate` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_company_candidate_tag_relation_tag` FOREIGN KEY (`tag_id`) REFERENCES `company_candidate_tag` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_company_candidate_tag_relation_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_company_candidate_tag_relation_status` CHECK (`status` IN (0, 1)),
  CONSTRAINT `chk_company_candidate_tag_relation_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Company candidate tag assignments';
