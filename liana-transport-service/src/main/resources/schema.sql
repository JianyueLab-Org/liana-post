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
  KEY `idx_transport_task_schedule` (`schedule_id`),
  CONSTRAINT `fk_transport_task_asset` FOREIGN KEY (`asset_id`) REFERENCES `transport_asset` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_transport_task_route` FOREIGN KEY (`route_id`) REFERENCES `transport_route` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_transport_task_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `transport_schedule` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `transport_asset` (`code`, `name`, `type`, `capacity`, `status`) VALUES
('S-001', 'Pacific Voyager', 'SHIP', 1200.00, 'AVAILABLE'),
('A-001', 'Sky Liner 7', 'AIRCRAFT', 180.00, 'IN_SERVICE'),
('T-001', 'Post Truck 12', 'TRUCK', 80.00, 'MAINTENANCE');

INSERT INTO `transport_route` (`route_code`, `origin_facility_id`, `destination_facility_id`, `transport_type`, `estimated_hours`, `status`) VALUES
('TR-A1-B1-SEA', 'A1', 'B1', 'SEA', 18.00, 'ACTIVE'),
('TR-A1-B1-AIR', 'A1', 'B1', 'AIR', 4.00, 'ACTIVE'),
('TR-B1-C1-LAND', 'B1', 'C1', 'LAND', 3.50, 'PLANNED');

INSERT INTO `transport_schedule` (`schedule_code`, `asset_id`, `route_id`, `departure_time`, `arrival_time`, `weekday`, `status`) VALUES
('TS-20260611-001', 1, 1, '2026-06-11 08:00:00', '2026-06-12 02:00:00', 'MON', 'ACTIVE'),
('TS-20260611-002', 2, 2, '2026-06-11 10:00:00', '2026-06-11 14:00:00', 'MON', 'PLANNED');

INSERT INTO `transport_task` (`task_code`, `dispatch_bag_id`, `asset_id`, `route_id`, `schedule_id`, `status`) VALUES
('TT-20260611-001', 1, 2, 2, 2, 'ASSIGNED');
