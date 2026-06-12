CREATE DATABASE IF NOT EXISTS `facility_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `facility_db`;

DROP TABLE IF EXISTS `facility_route`;
DROP TABLE IF EXISTS `facility`;
DROP TABLE IF EXISTS `facility_type`;

CREATE TABLE `facility_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_facility_type_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `facility` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `facility_code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `type_code` VARCHAR(64) NOT NULL,
  `parent_facility_code` VARCHAR(64) DEFAULT NULL COMMENT '上级设施节点编码 (如小岛网点的上级是区域枢纽)',
  `country_code` VARCHAR(8) NOT NULL DEFAULT 'LN',
  `province` VARCHAR(64) DEFAULT NULL,
  `city` VARCHAR(64) DEFAULT NULL,
  `address` VARCHAR(255) DEFAULT NULL,
  `latitude` DECIMAL(10,7) DEFAULT NULL,
  `longitude` DECIMAL(10,7) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_facility_code` (`facility_code`),
  KEY `idx_facility_type_code` (`type_code`),
  KEY `idx_parent_facility_code` (`parent_facility_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `facility_route` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `route_code` VARCHAR(64) NOT NULL,
  `origin_facility_code` VARCHAR(64) NOT NULL,
  `destination_facility_code` VARCHAR(64) NOT NULL,
  `transport_mode` VARCHAR(32) NOT NULL,
  `distance_km` DECIMAL(10,2) DEFAULT NULL,
  `estimated_hours` DECIMAL(10,2) DEFAULT NULL,
  `priority_level` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_facility_route_code` (`route_code`),
  KEY `idx_route_origin` (`origin_facility_code`),
  KEY `idx_route_destination` (`destination_facility_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;