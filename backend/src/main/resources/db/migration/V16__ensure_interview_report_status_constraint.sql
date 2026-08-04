-- Legacy databases were baselined at V8 without necessarily running the
-- original V8 status-constraint migration. Normalize the constraint so report
-- generation states 5-7 are accepted on every upgraded database.
SET @constraint_exists := (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'interview'
    AND CONSTRAINT_NAME = 'chk_interview_status'
    AND CONSTRAINT_TYPE = 'CHECK'
);

SET @drop_constraint_sql := IF(
  @constraint_exists > 0,
  'ALTER TABLE `interview` DROP CHECK `chk_interview_status`',
  'SELECT 1'
);

PREPARE drop_constraint_stmt FROM @drop_constraint_sql;
EXECUTE drop_constraint_stmt;
DEALLOCATE PREPARE drop_constraint_stmt;

ALTER TABLE `interview`
  ADD CONSTRAINT `chk_interview_status`
  CHECK (`status` IN (0, 1, 2, 3, 4, 5, 6, 7));
