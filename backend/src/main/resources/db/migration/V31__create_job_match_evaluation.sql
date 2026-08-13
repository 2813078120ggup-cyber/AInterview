-- Versioned, explainable recruitment match evaluations.
-- V30 is published and remains unchanged.

ALTER TABLE `job_application`
  ADD COLUMN `match_evaluation_version` INT NOT NULL DEFAULT 0 COMMENT 'Latest match evaluation version for this application' AFTER `match_version`,
  ADD KEY `idx_job_application_match_evaluation` (`match_evaluation_version`, `updated_at`);

CREATE TABLE IF NOT EXISTS `job_match_evaluation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT UNSIGNED NOT NULL,
  `analysis_id` BIGINT UNSIGNED DEFAULT NULL,
  `ai_task_id` BIGINT UNSIGNED DEFAULT NULL,
  `evaluation_version` INT NOT NULL,
  `resume_version` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/SUCCESS/FAILED',
  `position_snapshot` JSON NOT NULL,
  `resume_snapshot` JSON DEFAULT NULL,
  `rule_score` DECIMAL(5,2) DEFAULT NULL,
  `ai_score` DECIMAL(5,2) DEFAULT NULL,
  `final_score` DECIMAL(5,2) DEFAULT NULL,
  `strengths` JSON DEFAULT NULL,
  `gaps` JSON DEFAULT NULL,
  `evidence` JSON DEFAULT NULL,
  `confidence` VARCHAR(20) DEFAULT NULL,
  `provider_name` VARCHAR(64) DEFAULT NULL,
  `model_name` VARCHAR(128) DEFAULT NULL,
  `prompt_version` INT DEFAULT NULL,
  `error_message` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_match_evaluation_version` (`application_id`, `evaluation_version`),
  KEY `idx_job_match_evaluation_status` (`status`, `created_at`),
  KEY `idx_job_match_evaluation_task` (`ai_task_id`),
  CONSTRAINT `fk_job_match_evaluation_application` FOREIGN KEY (`application_id`) REFERENCES `job_application` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_job_match_evaluation_analysis` FOREIGN KEY (`analysis_id`) REFERENCES `candidate_resume_analysis` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_job_match_evaluation_task` FOREIGN KEY (`ai_task_id`) REFERENCES `ai_task` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_job_match_evaluation_status` CHECK (`status` IN ('PROCESSING', 'SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Versioned explainable recruitment match evaluations';
