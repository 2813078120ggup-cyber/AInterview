-- Recruitment platform foundation: companies, public jobs, applications and offline interviews.
-- Existing interview positions remain valid; recruitment-only columns are nullable for backwards compatibility.

CREATE TABLE IF NOT EXISTS `company` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `company_code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(160) NOT NULL,
  `short_name` VARCHAR(80) DEFAULT NULL,
  `logo_url` VARCHAR(512) DEFAULT NULL,
  `industry` VARCHAR(96) DEFAULT NULL,
  `company_size` VARCHAR(48) DEFAULT NULL,
  `city` VARCHAR(96) DEFAULT NULL,
  `description` TEXT DEFAULT NULL,
  `website_url` VARCHAR(512) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_by` BIGINT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_code` (`company_code`),
  KEY `idx_company_status_name` (`status`, `name`),
  CONSTRAINT `fk_company_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_company_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Recruiting companies';

ALTER TABLE `user`
  ADD COLUMN `company_id` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Tenant company for company-side accounts' AFTER `avatar_url`,
  ADD KEY `idx_user_company_status` (`company_id`, `status`),
  ADD CONSTRAINT `fk_user_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE SET NULL;

ALTER TABLE `job_position`
  ADD COLUMN `company_id` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Recruiting company; NULL means legacy practice position' AFTER `position_code`,
  ADD COLUMN `salary_min` INT DEFAULT NULL COMMENT 'Monthly salary in CNY thousands' AFTER `department`,
  ADD COLUMN `salary_max` INT DEFAULT NULL COMMENT 'Monthly salary in CNY thousands' AFTER `salary_min`,
  ADD COLUMN `city` VARCHAR(96) DEFAULT NULL AFTER `salary_max`,
  ADD COLUMN `experience_requirement` VARCHAR(64) DEFAULT NULL AFTER `city`,
  ADD COLUMN `education_requirement` VARCHAR(64) DEFAULT NULL AFTER `experience_requirement`,
  ADD COLUMN `job_type` VARCHAR(32) NOT NULL DEFAULT 'FULL_TIME' AFTER `education_requirement`,
  ADD COLUMN `requirements` TEXT DEFAULT NULL AFTER `description`,
  ADD COLUMN `skill_tags` JSON DEFAULT NULL AFTER `requirements`,
  ADD COLUMN `recruitment_status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' AFTER `status`,
  ADD COLUMN `published_at` DATETIME DEFAULT NULL AFTER `recruitment_status`,
  ADD COLUMN `expires_at` DATETIME DEFAULT NULL AFTER `published_at`,
  ADD KEY `idx_job_position_company_status` (`company_id`, `recruitment_status`, `updated_at`),
  ADD KEY `idx_job_position_hall` (`recruitment_status`, `city`, `published_at`),
  ADD CONSTRAINT `fk_job_position_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE RESTRICT,
  ADD CONSTRAINT `chk_job_position_salary` CHECK (`salary_min` IS NULL OR (`salary_min` >= 0 AND `salary_max` >= `salary_min`)),
  ADD CONSTRAINT `chk_job_position_job_type` CHECK (`job_type` IN ('FULL_TIME', 'PART_TIME', 'INTERNSHIP')),
  ADD CONSTRAINT `chk_job_position_recruitment_status` CHECK (`recruitment_status` IN ('DRAFT', 'PUBLISHED', 'CLOSED'));

CREATE TABLE IF NOT EXISTS `candidate_resume` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `candidate_id` BIGINT UNSIGNED NOT NULL,
  `media_id` BIGINT UNSIGNED DEFAULT NULL,
  `title` VARCHAR(160) NOT NULL,
  `file_name` VARCHAR(255) DEFAULT NULL,
  `summary` TEXT DEFAULT NULL,
  `skills` JSON DEFAULT NULL,
  `is_default` TINYINT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_candidate_resume_owner_default` (`candidate_id`, `is_default`, `status`),
  CONSTRAINT `fk_candidate_resume_candidate` FOREIGN KEY (`candidate_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_candidate_resume_media` FOREIGN KEY (`media_id`) REFERENCES `media_file` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_candidate_resume_default` CHECK (`is_default` IN (0, 1)),
  CONSTRAINT `chk_candidate_resume_status` CHECK (`status` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Candidate resume metadata backed by private media';

CREATE TABLE IF NOT EXISTS `job_application` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_no` VARCHAR(40) NOT NULL,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `position_id` BIGINT UNSIGNED NOT NULL,
  `candidate_id` BIGINT UNSIGNED NOT NULL,
  `resume_id` BIGINT UNSIGNED DEFAULT NULL,
  `interview_id` BIGINT UNSIGNED DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
  `source` VARCHAR(32) NOT NULL DEFAULT 'JOB_HALL',
  `match_score` DECIMAL(5,2) DEFAULT NULL,
  `match_summary` VARCHAR(1000) DEFAULT NULL,
  `match_details` JSON DEFAULT NULL,
  `candidate_message` VARCHAR(1000) DEFAULT NULL,
  `review_note` VARCHAR(2000) DEFAULT NULL,
  `submitted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `reviewed_at` DATETIME DEFAULT NULL,
  `version` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_application_no` (`application_no`),
  UNIQUE KEY `uk_job_application_candidate_position` (`candidate_id`, `position_id`),
  KEY `idx_job_application_candidate_status` (`candidate_id`, `status`, `submitted_at`),
  KEY `idx_job_application_company_status` (`company_id`, `status`, `updated_at`),
  KEY `idx_job_application_position_status` (`position_id`, `status`, `submitted_at`),
  CONSTRAINT `fk_job_application_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_job_application_position` FOREIGN KEY (`position_id`) REFERENCES `job_position` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_job_application_candidate` FOREIGN KEY (`candidate_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_job_application_resume` FOREIGN KEY (`resume_id`) REFERENCES `candidate_resume` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_job_application_interview` FOREIGN KEY (`interview_id`) REFERENCES `interview` (`id`) ON DELETE SET NULL,
  CONSTRAINT `chk_job_application_status` CHECK (`status` IN ('SUBMITTED', 'AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'UNDER_REVIEW', 'OFFLINE_INTERVIEW', 'REJECTED', 'HIRED')),
  CONSTRAINT `chk_job_application_match_score` CHECK (`match_score` IS NULL OR `match_score` BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Recruitment applications';

CREATE TABLE IF NOT EXISTS `job_application_status_history` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT UNSIGNED NOT NULL,
  `from_status` VARCHAR(32) DEFAULT NULL,
  `to_status` VARCHAR(32) NOT NULL,
  `operator_id` BIGINT UNSIGNED NOT NULL,
  `note` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_application_history_application_created` (`application_id`, `created_at`, `id`),
  CONSTRAINT `fk_application_history_application` FOREIGN KEY (`application_id`) REFERENCES `job_application` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_application_history_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Auditable application status transitions';

CREATE TABLE IF NOT EXISTS `offline_interview` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT UNSIGNED NOT NULL,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `candidate_id` BIGINT UNSIGNED NOT NULL,
  `scheduled_at` DATETIME NOT NULL,
  `duration_minutes` INT NOT NULL DEFAULT 60,
  `interview_type` VARCHAR(20) NOT NULL DEFAULT 'ONSITE',
  `location` VARCHAR(512) DEFAULT NULL,
  `meeting_url` VARCHAR(512) DEFAULT NULL,
  `contact_name` VARCHAR(80) DEFAULT NULL,
  `contact_phone` VARCHAR(32) DEFAULT NULL,
  `note` VARCHAR(1000) DEFAULT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_offline_interview_application` (`application_id`),
  KEY `idx_offline_interview_company_schedule` (`company_id`, `scheduled_at`),
  KEY `idx_offline_interview_candidate_schedule` (`candidate_id`, `scheduled_at`),
  CONSTRAINT `fk_offline_interview_application` FOREIGN KEY (`application_id`) REFERENCES `job_application` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_offline_interview_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_offline_interview_candidate` FOREIGN KEY (`candidate_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_offline_interview_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_offline_interview_duration` CHECK (`duration_minutes` BETWEEN 15 AND 480),
  CONSTRAINT `chk_offline_interview_type` CHECK (`interview_type` IN ('ONSITE', 'VIDEO', 'PHONE')),
  CONSTRAINT `chk_offline_interview_status` CHECK (`status` IN ('SCHEDULED', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Offline interview invitations';

INSERT INTO `role` (`role_code`, `role_name`, `description`, `status`) VALUES
  ('COMPANY_ADMIN', '企业管理员', '管理本企业招聘岗位、申请与面试邀请', 1)
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `description` = VALUES(`description`), `status` = VALUES(`status`);

INSERT IGNORE INTO `permission` (`permission_code`, `permission_name`, `resource_type`, `description`) VALUES
  ('company:read', '查看企业', 'company', '查看当前企业资料'),
  ('recruitment:position:read', '查看招聘岗位', 'recruitment_position', '查看当前企业招聘岗位'),
  ('recruitment:position:write', '管理招聘岗位', 'recruitment_position', '创建、发布和关闭当前企业岗位'),
  ('application:read', '查看招聘申请', 'job_application', '查看当前企业候选人申请'),
  ('application:review', '审核招聘申请', 'job_application', '推进申请状态并发出面试邀请');

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r JOIN `permission` p
  ON p.permission_code IN ('company:read', 'recruitment:position:read', 'recruitment:position:write', 'application:read', 'application:review')
WHERE r.role_code = 'COMPANY_ADMIN';

-- Portfolio-ready demo data. Company accounts reuse the baseline BCrypt demo hash.
SET @platform_admin := (SELECT id FROM `user` WHERE username = 'admin_zhang' LIMIT 1);

INSERT INTO `company` (`company_code`, `name`, `short_name`, `industry`, `company_size`, `city`, `description`, `status`, `created_by`) VALUES
  ('XINGYUN_TECH', '星云科技有限公司', '星云科技', '企业服务 / 人工智能', '500-999人', '北京', '专注企业智能化与云原生平台建设。', 1, @platform_admin),
  ('YUNQI_DIGITAL', '云启数字科技有限公司', '云启数字', '金融科技', '100-499人', '上海', '为金融与零售行业提供数据智能解决方案。', 1, @platform_admin)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `industry` = VALUES(`industry`), `company_size` = VALUES(`company_size`), `city` = VALUES(`city`), `description` = VALUES(`description`), `status` = VALUES(`status`);

SET @company_xingyun := (SELECT id FROM `company` WHERE company_code = 'XINGYUN_TECH');
SET @company_yunqi := (SELECT id FROM `company` WHERE company_code = 'YUNQI_DIGITAL');

INSERT INTO `user` (`username`, `password_hash`, `real_name`, `email`, `phone`, `company_id`, `status`) VALUES
  ('xingyun_hr', '$2a$10$27RjyF/XNbZUA5FD8bZvr.prelYf4keaWUO.V1RAJdEtG.QlJeYUC', '林晓雯', 'hr.xingyun@example.test', '13800002001', @company_xingyun, 1),
  ('yunqi_hr', '$2a$10$27RjyF/XNbZUA5FD8bZvr.prelYf4keaWUO.V1RAJdEtG.QlJeYUC', '周宁', 'hr.yunqi@example.test', '13800002002', @company_yunqi, 1)
ON DUPLICATE KEY UPDATE `real_name` = VALUES(`real_name`), `company_id` = VALUES(`company_id`), `status` = VALUES(`status`);

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`, `assigned_by`)
SELECT u.id, r.id, @platform_admin FROM `user` u JOIN `role` r ON r.role_code = 'COMPANY_ADMIN'
WHERE u.username IN ('xingyun_hr', 'yunqi_hr');

SET @xingyun_hr := (SELECT id FROM `user` WHERE username = 'xingyun_hr');
SET @yunqi_hr := (SELECT id FROM `user` WHERE username = 'yunqi_hr');

INSERT INTO `job_position`
  (`position_code`, `company_id`, `name`, `department`, `salary_min`, `salary_max`, `city`, `experience_requirement`, `education_requirement`, `job_type`, `description`, `requirements`, `skill_tags`, `status`, `recruitment_status`, `published_at`, `expires_at`, `created_by`)
VALUES
  ('XY-JAVA-2026-001', @company_xingyun, 'Java 开发工程师', '云平台研发部', 18, 28, '北京', '3-5年', '本科及以上', 'FULL_TIME', '参与企业级 AI 面试与人才服务平台的后端研发。', '熟悉 Java 17、Spring Boot、MySQL、Redis，具备微服务与性能优化经验。', JSON_ARRAY('Java', 'Spring Boot', 'MySQL', 'Redis', 'Spring Cloud'), 1, 'PUBLISHED', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 52 DAY), @xingyun_hr),
  ('XY-FE-2026-002', @company_xingyun, 'React 前端工程师', '体验技术部', 16, 25, '北京', '2-4年', '本科及以上', 'FULL_TIME', '负责招聘与智能面试产品的 Web 端体验建设。', '熟悉 React、TypeScript、工程化与响应式交互设计。', JSON_ARRAY('React', 'TypeScript', 'Vite', 'Tailwind CSS'), 1, 'PUBLISHED', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 55 DAY), @xingyun_hr),
  ('YQ-JAVA-2026-001', @company_yunqi, 'Java 后端开发工程师', '金融平台部', 20, 32, '上海', '3-5年', '本科及以上', 'FULL_TIME', '建设高可靠金融业务中台与数据服务。', '掌握 Java、Spring Cloud、MySQL，理解分布式事务、消息与服务治理。', JSON_ARRAY('Java', 'Spring Cloud', 'MySQL', 'Kafka'), 1, 'PUBLISHED', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 57 DAY), @yunqi_hr),
  ('YQ-DATA-2026-002', @company_yunqi, '数据分析实习生', '数据智能部', 5, 8, '上海', '经验不限', '本科及以上', 'INTERNSHIP', '参与招聘与业务分析的数据指标建设。', '具备 SQL 基础，了解 Python 与数据可视化。', JSON_ARRAY('SQL', 'Python', '数据分析'), 1, 'DRAFT', NULL, NULL, @yunqi_hr)
ON DUPLICATE KEY UPDATE `company_id` = VALUES(`company_id`), `name` = VALUES(`name`), `department` = VALUES(`department`), `salary_min` = VALUES(`salary_min`), `salary_max` = VALUES(`salary_max`), `city` = VALUES(`city`), `experience_requirement` = VALUES(`experience_requirement`), `education_requirement` = VALUES(`education_requirement`), `job_type` = VALUES(`job_type`), `description` = VALUES(`description`), `requirements` = VALUES(`requirements`), `skill_tags` = VALUES(`skill_tags`), `status` = VALUES(`status`), `recruitment_status` = VALUES(`recruitment_status`), `published_at` = VALUES(`published_at`), `expires_at` = VALUES(`expires_at`);

SET @candidate_liu := (SELECT id FROM `user` WHERE username = 'candidate_liu');
SET @candidate_sun := (SELECT id FROM `user` WHERE username = 'candidate_sun');

INSERT INTO `candidate_resume` (`candidate_id`, `title`, `file_name`, `summary`, `skills`, `is_default`, `status`)
SELECT @candidate_liu, 'Java 后端开发简历', '刘洋-Java开发工程师.pdf', '3 年 Java 后端经验，参与过 Spring Boot 微服务、MySQL 性能优化与 Redis 缓存治理。', JSON_ARRAY('Java', 'Spring Boot', 'MySQL', 'Redis'), 1, 1
WHERE @candidate_liu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `candidate_resume` WHERE candidate_id = @candidate_liu AND title = 'Java 后端开发简历');

INSERT INTO `candidate_resume` (`candidate_id`, `title`, `file_name`, `summary`, `skills`, `is_default`, `status`)
SELECT @candidate_sun, '前端开发简历', '孙悦-前端工程师.pdf', '2 年 React 与 TypeScript 项目经验，关注可访问性和复杂业务交互。', JSON_ARRAY('React', 'TypeScript', 'Vite'), 1, 1
WHERE @candidate_sun IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `candidate_resume` WHERE candidate_id = @candidate_sun AND title = '前端开发简历');

INSERT INTO `job_application`
  (`application_no`, `company_id`, `position_id`, `candidate_id`, `resume_id`, `status`, `source`, `match_score`, `match_summary`, `match_details`, `candidate_message`, `submitted_at`, `reviewed_at`)
SELECT 'APP-202608-0001', p.company_id, p.id, @candidate_liu, r.id, 'UNDER_REVIEW', 'JOB_HALL', 88.00,
       'Java 与 Spring Boot 经历匹配度较高，数据库和缓存经验符合岗位核心要求。',
       JSON_OBJECT('strengths', JSON_ARRAY('Java 基础扎实', '具备 Spring Boot 项目经验', '有 MySQL 优化实践'), 'risks', JSON_ARRAY('大型分布式系统经验需进一步核实')),
       '希望参与 AI 招聘平台与云原生架构建设。', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM `job_position` p JOIN `candidate_resume` r ON r.candidate_id = @candidate_liu AND r.is_default = 1
WHERE p.position_code = 'XY-JAVA-2026-001' AND NOT EXISTS (SELECT 1 FROM `job_application` WHERE application_no = 'APP-202608-0001') LIMIT 1;

INSERT INTO `job_application`
  (`application_no`, `company_id`, `position_id`, `candidate_id`, `resume_id`, `status`, `source`, `match_score`, `match_summary`, `match_details`, `candidate_message`, `submitted_at`)
SELECT 'APP-202608-0002', p.company_id, p.id, @candidate_sun, r.id, 'AI_INTERVIEW_PENDING', 'JOB_HALL', 91.00,
       'React、TypeScript 与工程化经历高度匹配，建议进入定向 AI 面试。',
       JSON_OBJECT('strengths', JSON_ARRAY('React 项目经验完整', '重视可访问性', '具备 TypeScript 工程化能力'), 'risks', JSON_ARRAY('复杂性能优化经验待验证')),
       '期待参与面向候选人的高质量产品体验建设。', DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM `job_position` p JOIN `candidate_resume` r ON r.candidate_id = @candidate_sun AND r.is_default = 1
WHERE p.position_code = 'XY-FE-2026-002' AND NOT EXISTS (SELECT 1 FROM `job_application` WHERE application_no = 'APP-202608-0002') LIMIT 1;

INSERT IGNORE INTO `job_application_status_history` (`application_id`, `from_status`, `to_status`, `operator_id`, `note`, `created_at`)
SELECT a.id, NULL, 'SUBMITTED', a.candidate_id, '候选人通过岗位大厅投递', a.submitted_at FROM `job_application` a WHERE a.application_no IN ('APP-202608-0001', 'APP-202608-0002');

INSERT INTO `job_application_status_history` (`application_id`, `from_status`, `to_status`, `operator_id`, `note`, `created_at`)
SELECT a.id, 'SUBMITTED', a.status, @xingyun_hr, '演示数据：完成初步筛选', COALESCE(a.reviewed_at, a.updated_at)
FROM `job_application` a
WHERE a.application_no IN ('APP-202608-0001', 'APP-202608-0002')
  AND a.status <> 'SUBMITTED'
  AND NOT EXISTS (SELECT 1 FROM `job_application_status_history` h WHERE h.application_id = a.id AND h.to_status = a.status);
