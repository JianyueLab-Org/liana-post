USE `oms_db`;

SET @mail_package_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mail'
    AND COLUMN_NAME = 'package_id'
);

SET @mail_dest_country_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mail'
    AND COLUMN_NAME = 'dest_country_code'
);

SET @mail_current_slot_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mail'
    AND COLUMN_NAME = 'current_slot'
);

SET @mail_destination_node_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mail'
    AND COLUMN_NAME = 'destination_node'
);

SET @mail_package_index_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mail'
    AND INDEX_NAME = 'idx_mail_package_id'
);

SET @mail_current_slot_index_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mail'
    AND INDEX_NAME = 'idx_mail_current_slot'
);

SET @mail_package_sql := IF(
  @mail_package_exists = 0,
  'ALTER TABLE `mail` ADD COLUMN `package_id` VARCHAR(32) DEFAULT NULL AFTER `bag_no`',
  'SELECT 1'
);

SET @mail_dest_country_sql := IF(
  @mail_dest_country_exists = 0,
  'ALTER TABLE `mail` ADD COLUMN `dest_country_code` VARCHAR(8) DEFAULT NULL COMMENT ''寄达国代码'' AFTER `mail_scope`',
  'SELECT 1'
);

SET @mail_current_slot_sql := IF(
  @mail_current_slot_exists = 0,
  'ALTER TABLE `mail` ADD COLUMN `current_slot` VARCHAR(64) DEFAULT NULL AFTER `current_facility_code`',
  'SELECT 1'
);

SET @mail_destination_node_sql := IF(
  @mail_destination_node_exists = 0,
  'ALTER TABLE `mail` ADD COLUMN `destination_node` VARCHAR(128) DEFAULT NULL AFTER `dest_facility_code`',
  'SELECT 1'
);

PREPARE stmt FROM @mail_package_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

PREPARE stmt FROM @mail_dest_country_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

PREPARE stmt FROM @mail_current_slot_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

PREPARE stmt FROM @mail_destination_node_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @mail_package_index_sql := IF(
  @mail_package_index_exists = 0,
  'ALTER TABLE `mail` ADD INDEX `idx_mail_package_id` (`package_id`)',
  'SELECT 1'
);

SET @mail_current_slot_index_sql := IF(
  @mail_current_slot_index_exists = 0,
  'ALTER TABLE `mail` ADD INDEX `idx_mail_current_slot` (`current_slot`)',
  'SELECT 1'
);

PREPARE stmt FROM @mail_package_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

PREPARE stmt FROM @mail_current_slot_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
