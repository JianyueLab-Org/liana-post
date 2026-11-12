USE `dispatch_db`;

SET @route_scope_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'route_rule'
    AND COLUMN_NAME = 'route_scope'
);

SET @dest_country_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'route_rule'
    AND COLUMN_NAME = 'dest_country_code'
);

SET @export_facility_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'route_rule'
    AND COLUMN_NAME = 'export_facility_code'
);

SET @scope_country_index_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'route_rule'
    AND INDEX_NAME = 'idx_route_rule_scope_country'
);

SET @route_scope_sql := IF(
  @route_scope_exists = 0,
  'ALTER TABLE `route_rule` ADD COLUMN `route_scope` VARCHAR(32) NOT NULL DEFAULT ''DOMESTIC'' AFTER `target_facility_code`',
  'SELECT 1'
);

SET @dest_country_sql := IF(
  @dest_country_exists = 0,
  'ALTER TABLE `route_rule` ADD COLUMN `dest_country_code` VARCHAR(8) DEFAULT NULL AFTER `route_scope`',
  'SELECT 1'
);

SET @export_facility_sql := IF(
  @export_facility_exists = 0,
  'ALTER TABLE `route_rule` ADD COLUMN `export_facility_code` VARCHAR(64) DEFAULT NULL AFTER `dest_country_code`',
  'SELECT 1'
);

PREPARE stmt FROM @route_scope_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

PREPARE stmt FROM @dest_country_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

PREPARE stmt FROM @export_facility_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `route_rule`
SET `route_scope` = 'DOMESTIC'
WHERE `route_scope` IS NULL OR `route_scope` = '';

SET @scope_country_index_sql := IF(
  @scope_country_index_exists = 0,
  'ALTER TABLE `route_rule` ADD INDEX `idx_route_rule_scope_country` (`source_facility_code`, `route_scope`, `dest_country_code`, `enabled`)',
  'SELECT 1'
);

PREPARE stmt FROM @scope_country_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
