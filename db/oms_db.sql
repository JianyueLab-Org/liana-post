CREATE DATABASE IF NOT EXISTS `oms_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `oms_db`;

DROP TABLE IF EXISTS `recipient`;
DROP TABLE IF EXISTS `sender`;
DROP TABLE IF EXISTS `mail`;
DROP TABLE IF EXISTS `mail_type`;

CREATE TABLE `mail_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `requires_signature` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mail_type_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sender` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `full_name` VARCHAR(128) NOT NULL,
  `phone` VARCHAR(32) NOT NULL,
  `id_type` VARCHAR(32) DEFAULT NULL,
  `id_number` VARCHAR(64) DEFAULT NULL,
  `address` VARCHAR(255) NOT NULL,
  `postcode` VARCHAR(16) DEFAULT NULL,
  `country_code` VARCHAR(8) NOT NULL DEFAULT 'LN',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sender_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `recipient` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `full_name` VARCHAR(128) NOT NULL,
  `phone` VARCHAR(32) NOT NULL,
  `address` VARCHAR(255) NOT NULL,
  `postcode` VARCHAR(16) DEFAULT NULL,
  `country_code` VARCHAR(8) NOT NULL DEFAULT 'LN',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recipient_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `mail` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `waybill_no` VARCHAR(13) NOT NULL,
  `bag_no` VARCHAR(32) DEFAULT NULL COMMENT '所属总包/邮袋号(白天收寄为空，晚上封发时填入)',
  `mail_type_code` VARCHAR(64) NOT NULL,
  `service_type` VARCHAR(32) DEFAULT NULL,
  `mail_scope` VARCHAR(32) NOT NULL DEFAULT 'DOMESTIC',
  `sender_id` BIGINT NOT NULL,
  `recipient_id` BIGINT NOT NULL,
  `origin_facility_code` VARCHAR(64) DEFAULT NULL,
  `current_facility_code` VARCHAR(64) DEFAULT NULL,
  `dest_facility_code` VARCHAR(64) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  `weight_grams` INT DEFAULT NULL,
  `declared_value` DECIMAL(12,2) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mail_waybill_no` (`waybill_no`),
  KEY `idx_mail_bag_no` (`bag_no`),
  KEY `idx_mail_status` (`status`),
  KEY `idx_mail_sender_id` (`sender_id`),
  KEY `idx_mail_recipient_id` (`recipient_id`),
  KEY `idx_mail_current_facility_code` (`current_facility_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `mail_type` (`code`, `name`, `description`, `requires_signature`) VALUES
('C', '包裹', '普通包裹', 0),
('R', '挂号信', '挂号信邮件', 1),
('E', 'EMS', '特快专递', 1);

INSERT INTO `sender` (`full_name`, `phone`, `id_type`, `id_number`, `address`, `postcode`, `country_code`) VALUES
('Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN');

INSERT INTO `recipient` (`full_name`, `phone`, `address`, `postcode`, `country_code`) VALUES
('Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN');

INSERT INTO `mail` (`waybill_no`, `bag_no`, `mail_type_code`, `service_type`, `mail_scope`, `sender_id`, `recipient_id`, `origin_facility_code`, `current_facility_code`, `dest_facility_code`, `status`, `weight_grams`, `declared_value`) VALUES
('EE123456785LN', NULL, 'R', 'STANDARD', 'DOMESTIC', 1, 1, 'B1', 'B1', 'C1', 'CREATED', 120, NULL);
