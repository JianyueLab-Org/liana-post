CREATE DATABASE IF NOT EXISTS `transport_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `transport_db`;

DROP TABLE IF EXISTS `transport_task`;
DROP TABLE IF EXISTS `transport_schedule`;
DROP TABLE IF EXISTS `transport_route`;
DROP TABLE IF EXISTS `transport_asset`;

CREATE TABLE `transport_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `type` VARCHAR(32) NOT NULL,
  `capacity` DECIMAL(12,2) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transport_asset_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `transport_route` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `route_code` VARCHAR(64) NOT NULL,
  `origin_facility_id` VARCHAR(64) NOT NULL,
  `destination_facility_id` VARCHAR(64) NOT NULL,
  `transport_type` VARCHAR(32) NOT NULL,
  `estimated_hours` DECIMAL(10,2) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transport_route_code` (`route_code`),
  KEY `idx_transport_route_origin` (`origin_facility_id`),
  KEY `idx_transport_route_destination` (`destination_facility_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `transport_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `schedule_code` VARCHAR(64) NOT NULL,
  `asset_id` BIGINT NOT NULL,
  `route_id` BIGINT NOT NULL,
  `departure_time` DATETIME NOT NULL,
  `arrival_time` DATETIME NOT NULL,
  `weekday` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transport_schedule_code` (`schedule_code`),
  KEY `idx_transport_schedule_asset` (`asset_id`),
  KEY `idx_transport_schedule_route` (`route_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `transport_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_code` VARCHAR(64) NOT NULL,
  `dispatch_bag_id` BIGINT DEFAULT NULL,
  `asset_id` BIGINT DEFAULT NULL,
  `route_id` BIGINT DEFAULT NULL,
  `schedule_id` BIGINT DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transport_task_code` (`task_code`),
  KEY `idx_transport_task_dispatch_bag` (`dispatch_bag_id`),
  KEY `idx_transport_task_asset` (`asset_id`),
  KEY `idx_transport_task_route` (`route_id`),
  KEY `idx_transport_task_schedule` (`schedule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
