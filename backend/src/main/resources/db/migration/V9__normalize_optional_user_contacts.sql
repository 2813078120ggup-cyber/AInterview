-- Optional email and phone values must use NULL rather than empty strings.
-- MySQL unique indexes allow multiple NULL values but only one empty string.
UPDATE `user`
SET `email` = NULL
WHERE `email` IS NOT NULL AND TRIM(`email`) = '';

UPDATE `user`
SET `phone` = NULL
WHERE `phone` IS NOT NULL AND TRIM(`phone`) = '';
