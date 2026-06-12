CREATE DATABASE IF NOT EXISTS `tracking_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `tracking_db`;

DROP TABLE IF EXISTS `tracking_event`;

CREATE TABLE `tracking_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_no` VARCHAR(64) NOT NULL,
  `waybill_no` VARCHAR(13) NOT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `event_time` DATETIME NOT NULL,
  `facility_code` VARCHAR(64) DEFAULT NULL,
  `facility_name` VARCHAR(128) DEFAULT NULL,
  `operator_id` BIGINT DEFAULT NULL,
  `operator_name` VARCHAR(128) DEFAULT NULL,
  `payload` JSON DEFAULT NULL,
  `source_service` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tracking_event_no` (`event_no`),
  KEY `idx_tracking_waybill_no` (`waybill_no`),
  KEY `idx_tracking_event_type` (`event_type`),
  KEY `idx_tracking_event_time` (`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;