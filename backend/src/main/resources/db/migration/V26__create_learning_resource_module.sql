-- Private learning resources, per-user viewing permissions, and persistent PDF annotations.

CREATE TABLE IF NOT EXISTS `learning_resource` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `public_id` CHAR(36) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `description` VARCHAR(1000) DEFAULT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  `allow_download` TINYINT NOT NULL DEFAULT 0,
  `current_version_id` BIGINT UNSIGNED DEFAULT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learning_resource_public_id` (`public_id`),
  KEY `idx_learning_resource_status_updated` (`status`, `updated_at`),
  CONSTRAINT `fk_learning_resource_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_learning_resource_status` CHECK (`status` IN ('DRAFT', 'PUBLISHED', 'OFFLINE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Private learning resources';

CREATE TABLE IF NOT EXISTS `learning_resource_version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `resource_id` BIGINT UNSIGNED NOT NULL,
  `version_no` INT NOT NULL,
  `media_id` BIGINT UNSIGNED NOT NULL,
  `original_name` VARCHAR(255) NOT NULL,
  `file_size` BIGINT UNSIGNED NOT NULL,
  `checksum_sha256` CHAR(64) NOT NULL,
  `page_count` INT NOT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learning_resource_version_no` (`resource_id`, `version_no`),
  UNIQUE KEY `uk_learning_resource_version_media` (`media_id`),
  KEY `idx_learning_resource_version_resource_created` (`resource_id`, `created_at`),
  CONSTRAINT `fk_learning_resource_version_resource` FOREIGN KEY (`resource_id`) REFERENCES `learning_resource` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_learning_resource_version_media` FOREIGN KEY (`media_id`) REFERENCES `media_file` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_learning_resource_version_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Learning resource PDF versions';

ALTER TABLE `learning_resource`
  ADD CONSTRAINT `fk_learning_resource_current_version`
  FOREIGN KEY (`current_version_id`) REFERENCES `learning_resource_version` (`id`) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS `learning_resource_permission` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `resource_id` BIGINT UNSIGNED NOT NULL,
  `subject_type` VARCHAR(16) NOT NULL DEFAULT 'USER',
  `subject_id` VARCHAR(64) NOT NULL,
  `can_view` TINYINT NOT NULL DEFAULT 1,
  `can_annotate` TINYINT NOT NULL DEFAULT 1,
  `expires_at` DATETIME DEFAULT NULL,
  `granted_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learning_resource_permission_subject` (`resource_id`, `subject_type`, `subject_id`),
  KEY `idx_learning_resource_permission_subject` (`subject_type`, `subject_id`, `expires_at`),
  CONSTRAINT `fk_learning_resource_permission_resource` FOREIGN KEY (`resource_id`) REFERENCES `learning_resource` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_learning_resource_permission_granted_by` FOREIGN KEY (`granted_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_learning_resource_permission_subject_type` CHECK (`subject_type` IN ('USER', 'ROLE')),
  CONSTRAINT `chk_learning_resource_permission_annotate` CHECK (`can_annotate` = 0 OR `can_view` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Learning resource permissions';

CREATE TABLE IF NOT EXISTS `learning_resource_annotation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `public_id` CHAR(36) NOT NULL,
  `resource_id` BIGINT UNSIGNED NOT NULL,
  `version_id` BIGINT UNSIGNED NOT NULL,
  `owner_user_id` BIGINT UNSIGNED NOT NULL,
  `page_index` INT NOT NULL,
  `annotation_type` VARCHAR(24) NOT NULL,
  `anchor_type` VARCHAR(16) NOT NULL DEFAULT 'POSITION',
  `geometry_json` LONGTEXT NOT NULL,
  `selected_text` TEXT DEFAULT NULL,
  `note_content` TEXT DEFAULT NULL,
  `style_json` VARCHAR(2000) DEFAULT NULL,
  `visibility` VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  `version` INT NOT NULL DEFAULT 1,
  `deleted_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learning_resource_annotation_public_id` (`public_id`),
  KEY `idx_learning_resource_annotation_owner_page` (`resource_id`, `version_id`, `owner_user_id`, `page_index`, `deleted_at`),
  CONSTRAINT `fk_learning_resource_annotation_resource` FOREIGN KEY (`resource_id`) REFERENCES `learning_resource` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_learning_resource_annotation_version` FOREIGN KEY (`version_id`) REFERENCES `learning_resource_version` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_learning_resource_annotation_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_learning_resource_annotation_type` CHECK (`annotation_type` IN ('HIGHLIGHT', 'UNDERLINE', 'STRIKEOUT', 'NOTE', 'RECTANGLE', 'INK')),
  CONSTRAINT `chk_learning_resource_annotation_anchor` CHECK (`anchor_type` IN ('POSITION', 'TEXT', 'PAGE')),
  CONSTRAINT `chk_learning_resource_annotation_visibility` CHECK (`visibility` IN ('PRIVATE', 'ADMIN_VISIBLE', 'PUBLIC'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User PDF annotations and notes';
