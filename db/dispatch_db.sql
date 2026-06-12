CREATE DATABASE IF NOT EXISTS `dispatch_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `dispatch_db`;

DROP TABLE IF EXISTS `handoff_record`;
DROP TABLE IF EXISTS `dispatch_batch`;
DROP TABLE IF EXISTS `dispatch_bag`;
DROP TABLE IF EXISTS `route_rule`;

CREATE TABLE `route_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `rule_code` VARCHAR(64) NOT NULL,
  `source_facility_code` VARCHAR(64) NOT NULL,
  `target_facility_code` VARCHAR(64) NOT NULL,
  `priority_level` INT NOT NULL DEFAULT 0,
  `transport_mode` VARCHAR(32) NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_rule_code` (`rule_code`),
  KEY `idx_route_rule_source` (`source_facility_code`),
  KEY `idx_route_rule_target` (`target_facility_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `dispatch_bag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `bag_no` VARCHAR(32) NOT NULL,
  `origin_facility_code` VARCHAR(64) NOT NULL,
  `destination_facility_code` VARCHAR(64) NOT NULL,
  `route_code` VARCHAR(64) DEFAULT NULL,
  `mail_no_list` JSON NOT NULL COMMENT '总包内包含的 UPU 邮件单号数组清单',
  `mail_type_code` VARCHAR(64) NOT NULL COMMENT '总包邮件类型: C(包裹), R(挂号信), E(EMS)',
  `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  `mail_count` INT NOT NULL DEFAULT 0,
  `total_weight_grams` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_bag_no` (`bag_no`),
  KEY `idx_dispatch_bag_status` (`status`),
  KEY `idx_dispatch_bag_origin` (`origin_facility_code`),
  KEY `idx_dispatch_bag_destination` (`destination_facility_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `dispatch_batch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `batch_no` VARCHAR(32) NOT NULL,
  `bag_no` VARCHAR(32) NOT NULL,
  `route_code` VARCHAR(64) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `approved_by` BIGINT DEFAULT NULL,
  `approved_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_batch_no` (`batch_no`),
  KEY `idx_dispatch_batch_bag_no` (`bag_no`),
  KEY `idx_dispatch_batch_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `handoff_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `handoff_no` VARCHAR(32) NOT NULL,
  `batch_no` VARCHAR(32) NOT NULL,
  `from_facility_code` VARCHAR(64) NOT NULL,
  `to_facility_code` VARCHAR(64) NOT NULL,
  `handoff_time` DATETIME NOT NULL,
  `receiver_id` BIGINT DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_handoff_no` (`handoff_no`),
  KEY `idx_handoff_batch_no` (`batch_no`),
  KEY `idx_handoff_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
