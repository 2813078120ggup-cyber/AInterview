-- Backfill completed legacy match results into the versioned evaluation history.
-- V30-V32 are published and remain unchanged. Legacy snapshots use version 0;
-- a new retry starts at version 1 and therefore cannot collide with this data.

INSERT INTO `job_match_evaluation` (
  `application_id`,
  `evaluation_version`,
  `resume_version`,
  `status`,
  `position_snapshot`,
  `resume_snapshot`,
  `rule_score`,
  `ai_score`,
  `final_score`,
  `summary`,
  `rule_matched_skills`,
  `matched_skills`,
  `strengths`,
  `gaps`,
  `risks`,
  `evidence`,
  `confidence`,
  `provider_name`,
  `model_name`,
  `recommendation`,
  `created_at`,
  `finished_at`
)
SELECT
  a.`id`,
  0,
  COALESCE(a.`match_version`, 0),
  'SUCCESS',
  JSON_OBJECT(
    'id', p.`id`,
    'positionCode', p.`position_code`,
    'name', p.`name`,
    'department', p.`department`,
    'requirements', p.`requirements`,
    'skillTags', COALESCE(p.`skill_tags`, JSON_ARRAY())
  ),
  CASE WHEN r.`id` IS NULL THEN JSON_OBJECT() ELSE JSON_OBJECT(
    'id', r.`id`,
    'title', r.`title`,
    'summary', r.`summary`,
    'skills', COALESCE(r.`skills`, JSON_ARRAY())
  ) END,
  NULL,
  NULL,
  a.`match_score`,
  a.`match_summary`,
  JSON_ARRAY(),
  COALESCE(JSON_EXTRACT(a.`match_details`, '$.matchedSkills'), JSON_ARRAY()),
  COALESCE(JSON_EXTRACT(a.`match_details`, '$.strengths'), JSON_ARRAY()),
  COALESCE(JSON_EXTRACT(a.`match_details`, '$.gaps'), JSON_ARRAY()),
  COALESCE(JSON_EXTRACT(a.`match_details`, '$.risks'), JSON_ARRAY()),
  COALESCE(JSON_EXTRACT(a.`match_details`, '$.evidence'), JSON_ARRAY()),
  'LEGACY',
  'legacy',
  'legacy',
  COALESCE(JSON_UNQUOTE(JSON_EXTRACT(a.`match_details`, '$.recommendation')), '保留历史匹配结果，以当前企业审核为准'),
  a.`created_at`,
  COALESCE(a.`match_completed_at`, a.`reviewed_at`, a.`updated_at`)
FROM `job_application` a
JOIN `job_position` p ON p.`id` = a.`position_id`
LEFT JOIN `candidate_resume` r ON r.`id` = a.`resume_id`
WHERE a.`match_score` IS NOT NULL
  AND a.`match_status` IN ('SUCCESS', 'MANUAL')
  AND NOT EXISTS (
    SELECT 1
    FROM `job_match_evaluation` e
    WHERE e.`application_id` = a.`id`
      AND e.`evaluation_version` = 0
  );
