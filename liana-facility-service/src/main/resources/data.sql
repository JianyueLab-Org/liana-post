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
  `status` TINYINT NOT NULL DEFAULT 1,
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
  `parent_facility_code` VARCHAR(64) DEFAULT NULL,
  `country_code` VARCHAR(32) NOT NULL DEFAULT 'LN',
  `province` VARCHAR(128) DEFAULT NULL,
  `city` VARCHAR(128) DEFAULT NULL,
  `address` VARCHAR(255) DEFAULT NULL,
  `latitude` DECIMAL(10,6) DEFAULT NULL,
  `longitude` DECIMAL(10,6) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_facility_code` (`facility_code`),
  KEY `idx_facility_type_code` (`type_code`),
  KEY `idx_facility_parent_code` (`parent_facility_code`)
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
  KEY `idx_facility_route_origin` (`origin_facility_code`),
  KEY `idx_facility_route_destination` (`destination_facility_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `facility_type` (`code`, `name`, `description`) VALUES
('POST_OFFICE', '邮局', '基层邮政网点'),
('TRANSFER_CENTER', '转运中心', '邮件集散与转运中心'),
('INTERNATIONAL_GATEWAY', '国际交换局', '国际邮件交换节点'),
('AIR_HUB', '航空节点', '空运中转节点'),
('SEA_HUB', '海运节点', '海运中转节点');

INSERT INTO `facility` (`facility_code`, `name`, `type_code`, `parent_facility_code`, `country_code`, `province`, `city`, `address`, `status`) VALUES
('A1', 'Liana Prime', 'TRANSFER_CENTER', NULL, 'LN', 'Liana Province', 'Liana City', 'A1主枢纽', 1),
('A2', 'International Exchange Bureau', 'INTERNATIONAL_GATEWAY', 'A1', 'LN', 'Liana Province', 'Liana City', 'A2国际互换局', 1),
('B1', 'Namoa Post Office', 'POST_OFFICE', 'A1', 'LN', 'Liana Province', 'Liana City', 'B1支局', 1),
('B2', 'Taviri Post Office', 'POST_OFFICE', 'A1', 'LN', 'Liana Province', 'Liana City', 'B2支局', 1),
('B3', 'Kelea Post Office', 'POST_OFFICE', 'A1', 'LN', 'Liana Province', 'Liana City', 'B3支局', 1),
('C1', 'Oro Island Office', 'POST_OFFICE', 'B1', 'LN', 'Liana Province', 'Liana City', 'C1岛屿网点', 1),
('C2', 'Miri Island Office', 'POST_OFFICE', 'B2', 'LN', 'Liana Province', 'Liana City', 'C2岛屿网点', 1),
('C3', 'Sela Island Office', 'POST_OFFICE', 'B3', 'LN', 'Liana Province', 'Liana City', 'C3岛屿网点', 1);

INSERT INTO `facility_route` (`route_code`, `origin_facility_code`, `destination_facility_code`, `transport_mode`, `priority_level`, `status`) VALUES
('R-A1-B1', 'A1', 'B1', 'LAND', 0, 1),
('R-A1-A2', 'A1', 'A2', 'AIR', 0, 1),
('R-A2-A1', 'A2', 'A1', 'AIR', 0, 1),
('R-A1-B2', 'A1', 'B2', 'LAND', 0, 1),
('R-A1-B3', 'A1', 'B3', 'LAND', 0, 1),
('R-B1-A1', 'B1', 'A1', 'LAND', 0, 1);
