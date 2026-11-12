-- Apply once to an existing OMS database before deploying the new OMS service.
USE `oms_db`;
ALTER TABLE `mail` ADD COLUMN `additional_services` VARCHAR(512) NOT NULL DEFAULT '' AFTER `mail_type_code`;
INSERT INTO `mail_type` (`code`, `name`, `description`, `requires_signature`)
SELECT 'U', '信函', 'letter with optional registration', 0
WHERE NOT EXISTS (SELECT 1 FROM `mail_type` WHERE `code` = 'U');
-- Historical R records keep their original product and barcode; the API reports REGISTRATION.
