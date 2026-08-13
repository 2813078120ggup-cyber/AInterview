-- Append-only server-side business operation audit log.
-- Do not update or delete historical rows from application code.
CREATE TABLE IF NOT EXISTS `operation_audit_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_id` VARCHAR(64) NOT NULL,
  `actor_id` BIGINT UNSIGNED DEFAULT NULL,
  `actor_role` VARCHAR(256) DEFAULT NULL,
  `company_id` BIGINT UNSIGNED DEFAULT NULL,
  `module` VARCHAR(64) NOT NULL,
  `action` VARCHAR(64) NOT NULL,
  `resource_type` VARCHAR(64) NOT NULL,
  `resource_id` VARCHAR(128) DEFAULT NULL,
  `result` VARCHAR(16) NOT NULL,
  `summary` VARCHAR(2000) NOT NULL,
  `ip_address` VARCHAR(64) DEFAULT NULL,
  `user_agent` VARCHAR(512) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_operation_audit_created` (`created_at`, `id`),
  KEY `idx_operation_audit_request` (`request_id`),
  KEY `idx_operation_audit_actor_created` (`actor_id`, `created_at`, `id`),
  KEY `idx_operation_audit_company_created` (`company_id`, `created_at`, `id`),
  KEY `idx_operation_audit_module_action_created` (`module`, `action`, `created_at`, `id`),
  KEY `idx_operation_audit_resource` (`resource_type`, `resource_id`, `created_at`, `id`),
  CONSTRAINT `chk_operation_audit_result` CHECK (`result` IN ('SUCCESS', 'FAILURE', 'DENIED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Append-only business operation audit log';
