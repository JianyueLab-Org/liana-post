-- Audited empty-schema baseline. No legacy business rows. UTC DATETIME(3).
-- Provenance and retained legacy contracts: database/inventory.md

CREATE TABLE `transport_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `type` VARCHAR(32) NOT NULL,
  `capacity` DECIMAL(12,2) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transport_asset_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `transport_route` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `route_code` VARCHAR(64) NOT NULL,
  `facility_route_code` VARCHAR(64) DEFAULT NULL,
  `origin_facility_id` VARCHAR(64) NOT NULL,
  `destination_facility_id` VARCHAR(64) NOT NULL,
  `transport_type` VARCHAR(32) NOT NULL,
  `estimated_hours` DECIMAL(10,2) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY `idx_transport_route_facility_edge` (`facility_route_code`),
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
  `departure_time` DATETIME(3) NOT NULL,
  `arrival_time` DATETIME(3) NOT NULL,
  `weekday` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
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
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
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

CREATE TABLE `transport_task_bag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `task_id` BIGINT NOT NULL,
  `dispatch_bag_id` BIGINT NOT NULL,
  `bag_no` VARCHAR(32) NOT NULL,
  `weight_grams` INT NOT NULL DEFAULT 0,
  `linked_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY `uk_transport_task_bag_dispatch` (`dispatch_bag_id`),
  KEY `idx_transport_task_bag_task` (`task_id`),
  CONSTRAINT `fk_transport_task_bag_task` FOREIGN KEY (`task_id`) REFERENCES `transport_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE demo_seed_history (
  seed_version VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL,
  applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
