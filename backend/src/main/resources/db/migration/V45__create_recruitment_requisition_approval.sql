CREATE TABLE IF NOT EXISTS `recruitment_requisition` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requisition_no` VARCHAR(64) NOT NULL,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `position_id` BIGINT UNSIGNED NOT NULL,
  `headcount_code` VARCHAR(64) NOT NULL,
  `requested_headcount` INT NOT NULL,
  `approved_headcount` INT DEFAULT NULL,
  `cost_center_code` VARCHAR(64) NOT NULL,
  `cost_center_name` VARCHAR(128) DEFAULT NULL,
  `budget_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `budget_currency` CHAR(3) NOT NULL DEFAULT 'CNY',
  `business_justification` VARCHAR(2000) NOT NULL,
  `approval_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `submitted_by` BIGINT UNSIGNED DEFAULT NULL,
  `submitted_at` DATETIME DEFAULT NULL,
  `reviewed_by` BIGINT UNSIGNED DEFAULT NULL,
  `reviewed_at` DATETIME DEFAULT NULL,
  `review_note` VARCHAR(1000) DEFAULT NULL,
  `frozen` TINYINT NOT NULL DEFAULT 0,
  `frozen_by` BIGINT UNSIGNED DEFAULT NULL,
  `frozen_at` DATETIME DEFAULT NULL,
  `freeze_reason` VARCHAR(1000) DEFAULT NULL,
  `version` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recruitment_requisition_no` (`requisition_no`),
  UNIQUE KEY `uk_recruitment_requisition_position` (`position_id`),
  UNIQUE KEY `uk_recruitment_requisition_company_headcount` (`company_id`, `headcount_code`),
  KEY `idx_recruitment_requisition_company_status` (`company_id`, `approval_status`, `updated_at`),
  KEY `idx_recruitment_requisition_admin_queue` (`approval_status`, `frozen`, `submitted_at`),
  CONSTRAINT `fk_recruitment_requisition_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_recruitment_requisition_position` FOREIGN KEY (`position_id`) REFERENCES `job_position` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_recruitment_requisition_submitter` FOREIGN KEY (`submitted_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_recruitment_requisition_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_recruitment_requisition_freezer` FOREIGN KEY (`frozen_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_recruitment_requisition_headcount` CHECK (`requested_headcount` BETWEEN 1 AND 1000 AND (`approved_headcount` IS NULL OR `approved_headcount` BETWEEN 1 AND `requested_headcount`)),
  CONSTRAINT `chk_recruitment_requisition_budget` CHECK (`budget_amount` >= 0),
  CONSTRAINT `chk_recruitment_requisition_status` CHECK (`approval_status` IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
  CONSTRAINT `chk_recruitment_requisition_frozen` CHECK (`frozen` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Recruitment requisitions and headcount budget approvals';

CREATE TABLE IF NOT EXISTS `recruitment_requisition_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requisition_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `from_status` VARCHAR(32) DEFAULT NULL,
  `to_status` VARCHAR(32) DEFAULT NULL,
  `operator_id` BIGINT UNSIGNED DEFAULT NULL,
  `note` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recruitment_requisition_event_timeline` (`requisition_id`, `created_at`, `id`),
  CONSTRAINT `fk_recruitment_requisition_event_requisition` FOREIGN KEY (`requisition_id`) REFERENCES `recruitment_requisition` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_recruitment_requisition_event_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Immutable recruitment requisition approval timeline';

INSERT INTO `recruitment_requisition`
  (`requisition_no`, `company_id`, `position_id`, `headcount_code`, `requested_headcount`, `approved_headcount`,
   `cost_center_code`, `cost_center_name`, `budget_amount`, `budget_currency`, `business_justification`,
   `approval_status`, `submitted_by`, `submitted_at`, `reviewed_by`, `reviewed_at`, `review_note`, `created_at`, `updated_at`)
SELECT CONCAT('REQ-LEGACY-', p.id), p.company_id, p.id, CONCAT('HC-LEGACY-', p.id), 1,
       CASE WHEN p.recruitment_status IN ('PUBLISHED', 'CLOSED') THEN 1 ELSE NULL END,
       'LEGACY', '历史数据迁移', 0.00, 'CNY', 'V45 迁移前创建的岗位，保留原有招聘状态。',
       CASE WHEN p.recruitment_status IN ('PUBLISHED', 'CLOSED') THEN 'APPROVED' ELSE 'DRAFT' END,
       CASE WHEN p.recruitment_status IN ('PUBLISHED', 'CLOSED') THEN p.created_by ELSE NULL END,
       CASE WHEN p.recruitment_status IN ('PUBLISHED', 'CLOSED') THEN COALESCE(p.published_at, p.created_at) ELSE NULL END,
       NULL,
       CASE WHEN p.recruitment_status IN ('PUBLISHED', 'CLOSED') THEN COALESCE(p.published_at, p.created_at) ELSE NULL END,
       CASE WHEN p.recruitment_status IN ('PUBLISHED', 'CLOSED') THEN '历史岗位自动迁移为已批准' ELSE NULL END,
       p.created_at, p.updated_at
FROM `job_position` p
WHERE p.company_id IS NOT NULL
  AND p.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM `recruitment_requisition` r WHERE r.position_id = p.id);

INSERT INTO `recruitment_requisition_event`
  (`requisition_id`, `event_type`, `from_status`, `to_status`, `operator_id`, `note`, `created_at`)
SELECT r.id,
       CASE WHEN r.approval_status = 'APPROVED' THEN 'MIGRATED_APPROVED' ELSE 'MIGRATED_DRAFT' END,
       NULL, r.approval_status, NULL, 'V45 历史岗位迁移', r.created_at
FROM `recruitment_requisition` r
WHERE NOT EXISTS (SELECT 1 FROM `recruitment_requisition_event` e WHERE e.requisition_id = r.id);
