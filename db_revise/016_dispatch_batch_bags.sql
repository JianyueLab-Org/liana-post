CREATE TABLE IF NOT EXISTS `dispatch_batch_bag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `batch_no` VARCHAR(32) NOT NULL,
  `bag_no` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_batch_bag` (`bag_no`),
  KEY `idx_dispatch_batch_bag_batch` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `dispatch_batch_bag` (`batch_no`, `bag_no`)
SELECT `batch_no`, `bag_no` FROM `dispatch_batch`;
