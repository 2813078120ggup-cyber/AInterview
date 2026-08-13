-- Complete the immutable explainability snapshot for each match evaluation.
-- V31 is published and remains unchanged.

ALTER TABLE `job_match_evaluation`
  ADD COLUMN `summary` VARCHAR(1000) DEFAULT NULL AFTER `final_score`,
  ADD COLUMN `rule_matched_skills` JSON DEFAULT NULL AFTER `summary`,
  ADD COLUMN `matched_skills` JSON DEFAULT NULL AFTER `rule_matched_skills`,
  ADD COLUMN `risks` JSON DEFAULT NULL AFTER `gaps`,
  ADD COLUMN `recommendation` VARCHAR(255) DEFAULT NULL AFTER `evidence`;

UPDATE `job_match_evaluation` e
JOIN `job_application` a ON a.`id` = e.`application_id`
SET e.`summary` = a.`match_summary`,
    e.`matched_skills` = CASE
      WHEN JSON_VALID(a.`match_details`) THEN JSON_EXTRACT(a.`match_details`, '$.matchedSkills')
      ELSE NULL
    END,
    e.`risks` = CASE
      WHEN JSON_VALID(a.`match_details`) THEN JSON_EXTRACT(a.`match_details`, '$.risks')
      ELSE NULL
    END,
    e.`recommendation` = CASE
      WHEN JSON_VALID(a.`match_details`) THEN JSON_UNQUOTE(JSON_EXTRACT(a.`match_details`, '$.recommendation'))
      ELSE NULL
    END
WHERE e.`summary` IS NULL;
