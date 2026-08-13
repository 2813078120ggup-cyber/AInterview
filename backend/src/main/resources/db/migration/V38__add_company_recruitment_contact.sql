-- Company settings fields for the HR-owned recruitment contact.
-- Published migrations V27-V37 are intentionally left unchanged.

ALTER TABLE `company`
  ADD COLUMN `recruitment_contact_name` VARCHAR(80) DEFAULT NULL AFTER `website_url`,
  ADD COLUMN `recruitment_contact_email` VARCHAR(160) DEFAULT NULL AFTER `recruitment_contact_name`,
  ADD COLUMN `recruitment_contact_phone` VARCHAR(32) DEFAULT NULL AFTER `recruitment_contact_email`;
