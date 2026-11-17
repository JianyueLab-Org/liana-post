-- Audited empty-schema baseline. No legacy business rows. UTC DATETIME(3).
-- Provenance and retained legacy contracts: database/inventory.md

CREATE TABLE `mail_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `requires_signature` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mail_type_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `service_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(32) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_type_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `country` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(8) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `english_name` VARCHAR(128) DEFAULT NULL,
  `postal_enabled` TINYINT NOT NULL DEFAULT 1,
  `upu_region` VARCHAR(64) DEFAULT NULL,
  `remark` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_country_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `country_service_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `country_code` VARCHAR(8) NOT NULL,
  `service_type_code` VARCHAR(32) NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_country_service_type` (`country_code`, `service_type_code`),
  KEY `idx_country_service_type_country` (`country_code`),
  KEY `idx_country_service_type_service` (`service_type_code`)
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
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
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
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_recipient_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `mail` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `waybill_no` VARCHAR(32) NOT NULL,
  `bag_no` VARCHAR(32) DEFAULT NULL COMMENT 'mail bag id',
  `package_id` VARCHAR(32) DEFAULT NULL,
  `mail_type_code` VARCHAR(64) NOT NULL,
  `additional_services` VARCHAR(512) NOT NULL DEFAULT '',
  `service_type` VARCHAR(32) DEFAULT NULL,
  `mail_scope` VARCHAR(32) NOT NULL DEFAULT 'DOMESTIC',
  `dest_country_code` VARCHAR(8) DEFAULT NULL,
  `sender_id` BIGINT NOT NULL,
  `recipient_id` BIGINT NOT NULL,
  `origin_facility_code` VARCHAR(64) DEFAULT NULL,
  `current_facility_code` VARCHAR(64) DEFAULT NULL,
  `current_slot` VARCHAR(64) DEFAULT NULL,
  `dest_facility_code` VARCHAR(64) DEFAULT NULL,
  `destination_node` VARCHAR(128) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  `weight_grams` INT DEFAULT NULL,
  `declared_value` DECIMAL(12,2) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mail_waybill_no` (`waybill_no`),
  KEY `idx_mail_bag_no` (`bag_no`),
  KEY `idx_mail_package_id` (`package_id`),
  KEY `idx_mail_current_slot` (`current_slot`),
  KEY `idx_mail_status` (`status`),
  KEY `idx_mail_sender_id` (`sender_id`),
  KEY `idx_mail_recipient_id` (`recipient_id`),
  KEY `idx_mail_current_facility_code` (`current_facility_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE oms_postage_acceptance (
    acceptance_id VARCHAR(36) PRIMARY KEY,
    created_by VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    waybill_no VARCHAR(32) UNIQUE,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE demo_seed_history (
  seed_version VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL,
  applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
