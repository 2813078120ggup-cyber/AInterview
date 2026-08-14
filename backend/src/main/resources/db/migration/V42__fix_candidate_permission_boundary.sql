-- Candidate capabilities are enforced by identity and owned-resource checks, not by the
-- platform/company permission matrix. Remove legacy relations and invalidate existing
-- candidate access tokens so stale permission authorities cannot remain effective.

START TRANSACTION;

DELETE rp
FROM `role_permission` rp
JOIN `role` r ON r.id = rp.role_id
WHERE r.role_code = 'CANDIDATE';

UPDATE `role`
SET `version` = `version` + 1
WHERE `role_code` = 'CANDIDATE';

UPDATE `user` u
JOIN `user_role` ur ON ur.user_id = u.id
JOIN `role` r ON r.id = ur.role_id AND r.role_code = 'CANDIDATE'
SET u.security_version = u.security_version + 1
WHERE u.deleted_at IS NULL;

COMMIT;
