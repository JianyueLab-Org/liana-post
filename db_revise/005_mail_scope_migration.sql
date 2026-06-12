USE `oms_db`;

ALTER TABLE `mail`
  ADD COLUMN `mail_scope` VARCHAR(32) NOT NULL DEFAULT 'DOMESTIC' COMMENT '邮件范围 DOMESTIC/INTERNATIONAL' AFTER `service_type`;

UPDATE `mail`
SET `mail_scope` = 'DOMESTIC'
WHERE `mail_scope` IS NULL OR `mail_scope` = '';
