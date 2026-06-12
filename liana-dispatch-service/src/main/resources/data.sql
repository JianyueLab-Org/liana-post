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
  `transport_task_code` VARCHAR(64) DEFAULT NULL,
  `mail_no_list` JSON NOT NULL,
  `mail_type_code` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  `mail_count` INT NOT NULL DEFAULT 0,
  `total_weight_grams` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_bag_no` (`bag_no`),
  KEY `idx_dispatch_bag_transport_task_code` (`transport_task_code`),
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
  `bag_no` VARCHAR(32) NOT NULL,
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

INSERT INTO `route_rule` (`rule_code`, `source_facility_code`, `target_facility_code`, `priority_level`, `transport_mode`, `enabled`) VALUES
('B1-C1-TRUCK', 'B1', 'C1', 1, 'TRUCK', 1),
('B1-C1-AIR', 'B1', 'C1', 2, 'AIR', 1);

INSERT INTO `dispatch_bag` (`bag_no`, `origin_facility_code`, `destination_facility_code`, `route_code`, `transport_task_code`, `mail_no_list`, `mail_type_code`, `status`, `mail_count`, `total_weight_grams`) VALUES
('B202606090001', 'B1', 'C1', 'B1-C1-TRUCK', JSON_ARRAY('LYN0000000001LN'), 'R', 'CREATED', 1, 120),
('B202606090002', 'B1', 'A1', 'B1-C1-TRUCK', JSON_ARRAY('LYN0000000002LN'), 'C', 'REVIEWED', 1, 130);

INSERT INTO `dispatch_batch` (`batch_no`, `bag_no`, `route_code`, `status`) VALUES
('D202606090001', 'B202606090001', 'B1-C1-TRUCK', 'PENDING');

INSERT INTO `handoff_record` (`handoff_no`, `bag_no`, `batch_no`, `from_facility_code`, `to_facility_code`, `handoff_time`, `status`) VALUES
('H202606090001', 'B202606090001', 'D202606090001', 'B1', 'C1', '2026-06-09 08:00:00', 'PENDING');
