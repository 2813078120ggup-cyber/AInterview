ALTER TABLE `interview`
  ADD COLUMN `active_question_index` INT UNSIGNED NOT NULL DEFAULT 0
    COMMENT 'Zero-based active question index used to resume an interview' AFTER `ended_at`,
  ADD COLUMN `progress_updated_at` DATETIME DEFAULT NULL
    COMMENT 'Last time the candidate changed the active question' AFTER `active_question_index`;

UPDATE `interview` i
SET i.`active_question_index` = COALESCE(
      (
        SELECT GREATEST(MAX(iq.`sequence_no`) - 1, 0)
        FROM `interview_question` iq
        INNER JOIN `interview_answer` ia ON ia.`interview_question_id` = iq.`id`
        WHERE iq.`interview_id` = i.`id`
      ),
      0
    ),
    i.`progress_updated_at` = COALESCE(i.`updated_at`, CURRENT_TIMESTAMP)
WHERE i.`status` = 1;
