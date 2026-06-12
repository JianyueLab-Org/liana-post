USE `oms_db`;

CREATE TABLE IF NOT EXISTS `service_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(32) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_type_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `country` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(8) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `english_name` VARCHAR(128) DEFAULT NULL,
  `postal_enabled` TINYINT NOT NULL DEFAULT 1,
  `upu_region` VARCHAR(64) DEFAULT NULL,
  `remark` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_country_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `country_service_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `country_code` VARCHAR(8) NOT NULL,
  `service_type_code` VARCHAR(32) NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_country_service_type` (`country_code`, `service_type_code`),
  KEY `idx_country_service_type_country` (`country_code`),
  KEY `idx_country_service_type_service` (`service_type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @dest_country_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mail'
    AND COLUMN_NAME = 'dest_country_code'
);

SET @dest_country_sql := IF(
  @dest_country_exists = 0,
  'ALTER TABLE `mail` ADD COLUMN `dest_country_code` VARCHAR(8) DEFAULT NULL COMMENT ''寄达国代码'' AFTER `mail_scope`',
  'SELECT 1'
);

PREPARE stmt FROM @dest_country_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `service_type` (`code`, `name`, `description`, `enabled`) VALUES
('AIR', '航空', '航空运输服务', 1),
('SAL', 'SAL', '空陆联运服务', 1),
('SEA', '海运', '海运运输服务', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `enabled` = VALUES(`enabled`);

INSERT INTO `country` (`code`, `name`, `english_name`, `postal_enabled`, `upu_region`, `remark`) VALUES
('LN', '利亚纳', 'Liana', 1, 'OCEANIA', '默认本国示例'),
('HK', '中国香港', 'Hong Kong', 1, 'ASIA_PACIFIC', 'UPU示例目的国'),
('JP', '日本', 'Japan', 1, 'ASIA_PACIFIC', 'UPU示例目的国'),
('US', '美国', 'United States', 1, 'NORTH_AMERICA', 'UPU示例目的国'),
('DE', '德国', 'Germany', 0, 'EUROPE', '示例：当前未通邮')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `english_name` = VALUES(`english_name`),
  `postal_enabled` = VALUES(`postal_enabled`),
  `upu_region` = VALUES(`upu_region`),
  `remark` = VALUES(`remark`);

INSERT INTO `country_service_type` (`country_code`, `service_type_code`, `enabled`) VALUES
('LN', 'AIR', 1),
('LN', 'SAL', 1),
('LN', 'SEA', 1),
('HK', 'AIR', 1),
('HK', 'SAL', 1),
('HK', 'SEA', 1),
('JP', 'AIR', 1),
('JP', 'SAL', 1),
('JP', 'SEA', 1),
('US', 'AIR', 1),
('US', 'SAL', 1),
('US', 'SEA', 1),
('DE', 'AIR', 0),
('DE', 'SAL', 0),
('DE', 'SEA', 0)
ON DUPLICATE KEY UPDATE
  `enabled` = VALUES(`enabled`);

UPDATE `mail`
SET `mail_scope` = UPPER(COALESCE(NULLIF(`mail_scope`, ''), 'DOMESTIC')),
    `service_type` = UPPER(COALESCE(NULLIF(`service_type`, ''), 'AIR')),
    `dest_country_code` = CASE
      WHEN UPPER(COALESCE(NULLIF(`mail_scope`, ''), 'DOMESTIC')) = 'INTERNATIONAL' THEN COALESCE(NULLIF(`dest_country_code`, ''), 'HK')
      ELSE NULL
    END;
