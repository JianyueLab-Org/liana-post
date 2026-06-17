CREATE DATABASE IF NOT EXISTS `sorting_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `sorting_db`;

DROP TABLE IF EXISTS `discrepancy_verification`;
DROP TABLE IF EXISTS `package_item_line`;
DROP TABLE IF EXISTS `sorting_manifest_item`;
DROP TABLE IF EXISTS `sorting_manifest`;
DROP TABLE IF EXISTS `sorting_total_package`;

CREATE TABLE `sorting_total_package` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `package_no` VARCHAR(64) NOT NULL,
  `package_level` VARCHAR(16) NOT NULL,
  `package_status` VARCHAR(16) NOT NULL,
  `source_org_code` VARCHAR(32) NOT NULL,
  `destination_org_code` VARCHAR(32) DEFAULT NULL,
  `parent_package_no` VARCHAR(64) DEFAULT NULL,
  `manifest_no` VARCHAR(64) DEFAULT NULL,
  `prealert_flag` TINYINT NOT NULL DEFAULT 0,
  `sealed_at` DATETIME DEFAULT NULL,
  `dispatched_at` DATETIME DEFAULT NULL,
  `received_at` DATETIME DEFAULT NULL,
  `opened_at` DATETIME DEFAULT NULL,
  `terminal_reason` VARCHAR(64) DEFAULT NULL,
  `version` BIGINT NOT NULL DEFAULT 0,
  `extra` JSON DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sorting_total_package_no` (`package_no`),
  KEY `idx_sorting_total_package_status` (`package_status`, `source_org_code`, `updated_at`),
  KEY `idx_sorting_total_package_manifest` (`manifest_no`),
  KEY `idx_sorting_total_package_parent` (`parent_package_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sorting_manifest` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `manifest_no` VARCHAR(64) NOT NULL,
  `manifest_type` VARCHAR(16) NOT NULL,
  `source_org_code` VARCHAR(32) NOT NULL,
  `destination_org_code` VARCHAR(32) NOT NULL,
  `batch_no` VARCHAR(64) NOT NULL,
  `manifest_status` VARCHAR(16) NOT NULL,
  `prealert_flag` TINYINT NOT NULL DEFAULT 1,
  `expected_package_qty` INT NOT NULL DEFAULT 0,
  `expected_item_qty` INT NOT NULL DEFAULT 0,
  `eta_time` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sorting_manifest_no` (`manifest_no`),
  KEY `idx_sorting_manifest_batch` (`batch_no`, `manifest_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sorting_manifest_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `manifest_no` VARCHAR(64) NOT NULL,
  `item_no` VARCHAR(64) NOT NULL,
  `expected_package_no` VARCHAR(64) DEFAULT NULL,
  `expected_seq_no` INT DEFAULT NULL,
  `expected_route_code` VARCHAR(32) DEFAULT NULL,
  `expected_qty` INT NOT NULL DEFAULT 1,
  `item_status` VARCHAR(16) NOT NULL DEFAULT 'EXPECTED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sorting_manifest_item` (`manifest_no`, `item_no`),
  KEY `idx_sorting_manifest_item_no` (`item_no`),
  KEY `idx_sorting_manifest_item_manifest` (`manifest_no`, `item_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `package_item_line` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `biz_line_no` VARCHAR(64) NOT NULL,
  `idempotency_key` VARCHAR(80) NOT NULL,
  `item_no` VARCHAR(64) NOT NULL,
  `action_type` VARCHAR(16) NOT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `scan_mode` VARCHAR(16) NOT NULL,
  `from_package_no` VARCHAR(64) DEFAULT NULL,
  `to_package_no` VARCHAR(64) DEFAULT NULL,
  `parent_line_id` BIGINT DEFAULT NULL,
  `manifest_no` VARCHAR(64) DEFAULT NULL,
  `scan_batch_no` VARCHAR(64) DEFAULT NULL,
  `station_code` VARCHAR(32) NOT NULL,
  `source_center_code` VARCHAR(32) DEFAULT NULL,
  `target_center_code` VARCHAR(32) DEFAULT NULL,
  `operator_id` VARCHAR(32) DEFAULT NULL,
  `device_id` VARCHAR(64) DEFAULT NULL,
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `line_status` VARCHAR(16) NOT NULL DEFAULT 'VALID',
  `ext` JSON DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_item_line_biz` (`biz_line_no`),
  UNIQUE KEY `uk_package_item_line_idem` (`idempotency_key`),
  KEY `idx_pil_item_time` (`item_no`, `event_time`),
  KEY `idx_pil_from_pkg` (`from_package_no`, `event_time`),
  KEY `idx_pil_to_pkg` (`to_package_no`, `event_time`),
  KEY `idx_pil_manifest` (`manifest_no`, `event_time`),
  KEY `idx_pil_scan_batch` (`scan_batch_no`, `event_time`),
  KEY `idx_pil_station_time` (`station_code`, `event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `discrepancy_verification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `verification_no` VARCHAR(64) NOT NULL,
  `manifest_no` VARCHAR(64) DEFAULT NULL,
  `package_no` VARCHAR(64) DEFAULT NULL,
  `item_no` VARCHAR(64) DEFAULT NULL,
  `discrepancy_type` VARCHAR(16) NOT NULL,
  `discrepancy_source` VARCHAR(16) NOT NULL,
  `expected_qty` INT NOT NULL DEFAULT 0,
  `actual_qty` INT NOT NULL DEFAULT 0,
  `exception_level` VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  `status` VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  `evidence` JSON DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` DATETIME DEFAULT NULL,
  `resolver_id` VARCHAR(32) DEFAULT NULL,
  `remark` VARCHAR(256) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_discrepancy_verification_no` (`verification_no`),
  KEY `idx_dv_manifest` (`manifest_no`, `discrepancy_type`, `status`),
  KEY `idx_dv_package` (`package_no`, `status`),
  KEY `idx_dv_item` (`item_no`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
