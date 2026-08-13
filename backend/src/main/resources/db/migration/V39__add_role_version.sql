-- Optimistic concurrency protection for administrator role and permission edits.
-- Published migrations V27-V38 are intentionally left unchanged.

ALTER TABLE `role`
  ADD COLUMN `version` INT NOT NULL DEFAULT 0 AFTER `status`;
