CREATE TABLE `interview_recording` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `interview_id` BIGINT UNSIGNED NOT NULL,
  `mode` VARCHAR(16) NOT NULL COMMENT 'TEXT, AUDIO, VIDEO',
  `status` VARCHAR(16) NOT NULL COMMENT 'SELECTED, RECORDING, COMPLETED, FAILED',
  `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `ended_at` DATETIME DEFAULT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_recording_interview` (`interview_id`),
  KEY `idx_interview_recording_creator` (`created_by`, `created_at`),
  CONSTRAINT `fk_interview_recording_interview` FOREIGN KEY (`interview_id`) REFERENCES `interview` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_interview_recording_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_interview_recording_mode` CHECK (`mode` IN ('TEXT', 'AUDIO', 'VIDEO')),
  CONSTRAINT `chk_interview_recording_status` CHECK (`status` IN ('SELECTED', 'RECORDING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Interview mode and recording session';

CREATE TABLE `interview_recording_segment` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `recording_id` BIGINT UNSIGNED NOT NULL,
  `interview_question_id` BIGINT UNSIGNED NOT NULL,
  `media_id` BIGINT UNSIGNED NOT NULL,
  `segment_no` INT UNSIGNED NOT NULL,
  `started_offset_ms` BIGINT UNSIGNED NOT NULL,
  `ended_offset_ms` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recording_segment_no` (`recording_id`, `segment_no`),
  KEY `idx_recording_segment_question` (`recording_id`, `interview_question_id`, `started_offset_ms`),
  KEY `idx_recording_segment_media` (`media_id`),
  CONSTRAINT `fk_recording_segment_recording` FOREIGN KEY (`recording_id`) REFERENCES `interview_recording` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_recording_segment_question` FOREIGN KEY (`interview_question_id`) REFERENCES `interview_question` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_recording_segment_media` FOREIGN KEY (`media_id`) REFERENCES `media_file` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_recording_segment_time` CHECK (`ended_offset_ms` >= `started_offset_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Playable media segment for one interview question';

CREATE TABLE `interview_timeline_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `recording_id` BIGINT UNSIGNED NOT NULL,
  `interview_question_id` BIGINT UNSIGNED DEFAULT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `offset_ms` BIGINT UNSIGNED NOT NULL,
  `content` VARCHAR(4000) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_timeline_recording_offset` (`recording_id`, `offset_ms`, `id`),
  KEY `idx_timeline_question_offset` (`interview_question_id`, `offset_ms`),
  CONSTRAINT `fk_timeline_recording` FOREIGN KEY (`recording_id`) REFERENCES `interview_recording` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_timeline_question` FOREIGN KEY (`interview_question_id`) REFERENCES `interview_question` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Question, answer and follow-up events aligned to recording time';
