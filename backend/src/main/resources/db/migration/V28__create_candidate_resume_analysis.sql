-- Candidate resume upload lifecycle and versioned parsing results.
-- Existing V27 demo resumes without a media file remain MANUAL and are not reprocessed automatically.

ALTER TABLE `candidate_resume`
  ADD COLUMN `parse_status` VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/PENDING/PROCESSING/SUCCESS/FAILED' AFTER `skills`,
  ADD COLUMN `parse_version` INT NOT NULL DEFAULT 0 COMMENT 'Current analysis version' AFTER `parse_status`,
  ADD COLUMN `parse_error` VARCHAR(1000) DEFAULT NULL COMMENT 'Sanitized parsing failure message' AFTER `parse_version`,
  ADD COLUMN `parsed_at` DATETIME DEFAULT NULL AFTER `parse_error`,
  ADD COLUMN `content_hash` VARCHAR(64) DEFAULT NULL COMMENT 'Uploaded file SHA-256' AFTER `parsed_at`,
  ADD KEY `idx_candidate_resume_parse_status` (`candidate_id`, `parse_status`, `updated_at`),
  ADD KEY `idx_candidate_resume_content_hash` (`candidate_id`, `content_hash`);

CREATE TABLE IF NOT EXISTS `candidate_resume_analysis` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `resume_id` BIGINT UNSIGNED NOT NULL,
  `analysis_version` INT NOT NULL,
  `ai_task_id` BIGINT UNSIGNED DEFAULT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `extractor_version` VARCHAR(32) NOT NULL DEFAULT 'resume-extractor-v1',
  `extracted_text` LONGTEXT DEFAULT NULL COMMENT 'Private parsed resume text; never returned to company list APIs',
  `profile_json` JSON DEFAULT NULL COMMENT 'Structured, evidence-first candidate profile',
  `error_message` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_candidate_resume_analysis_version` (`resume_id`, `analysis_version`),
  KEY `idx_candidate_resume_analysis_task` (`ai_task_id`),
  KEY `idx_candidate_resume_analysis_status` (`status`, `created_at`),
  CONSTRAINT `fk_candidate_resume_analysis_resume` FOREIGN KEY (`resume_id`) REFERENCES `candidate_resume` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_candidate_resume_analysis_task` FOREIGN KEY (`ai_task_id`) REFERENCES `ai_task` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_candidate_resume_analysis_status` CHECK (`status` IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Versioned candidate resume parsing results';

UPDATE `candidate_resume`
SET `parse_status` = 'MANUAL'
WHERE `parse_status` IS NULL OR `parse_status` = '';
