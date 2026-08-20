-- Public enterprise registration needs an optional legal identity alongside the
-- existing tenant profile.  NULL remains allowed for legacy/admin-created rows;
-- the unique key prevents two independently registered tenants from claiming the
-- same supplied business-license identifier.
ALTER TABLE `company`
  ADD COLUMN `business_license_no` VARCHAR(64) DEFAULT NULL AFTER `company_code`,
  ADD COLUMN `legal_representative` VARCHAR(64) DEFAULT NULL AFTER `name`,
  ADD UNIQUE KEY `uk_company_business_license_no` (`business_license_no`);
