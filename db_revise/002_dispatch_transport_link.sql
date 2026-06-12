USE `dispatch_db`;

ALTER TABLE `dispatch_bag`
  ADD COLUMN `transport_task_code` VARCHAR(64) DEFAULT NULL COMMENT '关联运输任务编码' AFTER `route_code`;

CREATE INDEX `idx_dispatch_bag_transport_task_code` ON `dispatch_bag` (`transport_task_code`);

CREATE TABLE IF NOT EXISTS `dispatch_transport_task_link` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_bag_id` BIGINT NOT NULL,
  `transport_task_code` VARCHAR(64) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_transport_task_link_bag` (`dispatch_bag_id`),
  UNIQUE KEY `uk_dispatch_transport_task_link_task` (`transport_task_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
