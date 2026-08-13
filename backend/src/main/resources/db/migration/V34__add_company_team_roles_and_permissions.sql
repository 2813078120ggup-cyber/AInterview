-- Company team roles and fine-grained permissions.
-- V27-V33 are published migrations and must not be modified.

INSERT INTO `role` (`role_code`, `role_name`, `description`, `status`) VALUES
  ('COMPANY_ADMIN', '企业管理员', '拥有本企业全部招聘与团队管理权限', 1),
  ('COMPANY_RECRUITER', '企业招聘专员', '管理本企业岗位、申请、面试和报告，但不能管理成员', 1),
  ('COMPANY_INTERVIEWER', '企业面试官', '仅查看被授权候选人/面试并提交面试评价', 1)
ON DUPLICATE KEY UPDATE
  `role_name` = VALUES(`role_name`),
  `description` = VALUES(`description`),
  `status` = VALUES(`status`);

INSERT IGNORE INTO `permission` (`permission_code`, `permission_name`, `resource_type`, `description`) VALUES
  ('company:read', '查看企业', 'company', '查看当前企业资料'),
  ('company:write', '修改企业', 'company', '修改当前企业资料'),
  ('company:team:manage', '管理企业成员', 'company_team', '创建、停用和分配本企业成员角色'),
  ('recruitment:position:read', '查看招聘岗位', 'recruitment_position', '查看当前企业招聘岗位'),
  ('recruitment:position:write', '管理招聘岗位', 'recruitment_position', '创建和编辑当前企业岗位'),
  ('recruitment:position:publish', '发布招聘岗位', 'recruitment_position', '发布或下线当前企业岗位'),
  ('application:read', '查看招聘申请', 'job_application', '查看当前企业候选人申请'),
  ('application:review', '审核招聘申请', 'job_application', '推进申请状态并处理申请评估'),
  ('application:export', '导出招聘申请', 'job_application', '导出当前企业招聘申请'),
  ('interview:create', '创建招聘面试', 'interview', '创建和邀请当前企业招聘面试'),
  ('interview:review', '提交面试评价', 'interview', '提交授权面试的人工评价'),
  ('report:read', '查看招聘报告', 'report', '查看当前企业申请关联报告'),
  ('analytics:read', '查看招聘分析', 'analytics', '查看当前企业招聘分析数据');

-- interview:read was introduced by the original RBAC baseline and is reused
-- for the first-stage COMPANY_INTERVIEWER read boundary.
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p
  ON p.permission_code IN (
    'company:read', 'company:write', 'company:team:manage',
    'recruitment:position:read', 'recruitment:position:write', 'recruitment:position:publish',
    'application:read', 'application:review', 'application:export',
    'interview:read', 'interview:create', 'interview:review',
    'report:read', 'analytics:read'
  )
WHERE r.role_code = 'COMPANY_ADMIN';

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p
  ON p.permission_code IN (
    'company:read',
    'recruitment:position:read', 'recruitment:position:write', 'recruitment:position:publish',
    'application:read', 'application:review', 'application:export',
    'interview:read', 'interview:create', 'interview:review',
    'report:read', 'analytics:read'
  )
WHERE r.role_code = 'COMPANY_RECRUITER';

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p
  ON p.permission_code IN ('company:read', 'application:read', 'interview:read', 'interview:review')
WHERE r.role_code = 'COMPANY_INTERVIEWER';
