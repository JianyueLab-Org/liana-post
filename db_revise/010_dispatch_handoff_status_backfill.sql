USE `dispatch_db`;

SET @handoff_bag_index_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'handoff_record'
    AND INDEX_NAME = 'idx_handoff_bag_no'
);

SET @handoff_bag_index_sql := IF(
  @handoff_bag_index_exists = 0,
  'ALTER TABLE `handoff_record` ADD INDEX `idx_handoff_bag_no` (`bag_no`, `status`)',
  'SELECT 1'
);

PREPARE stmt FROM @handoff_bag_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `dispatch_bag` bag
JOIN (
  SELECT
    `bag_no`,
    MAX(COALESCE(`updated_at`, `handoff_time`, `created_at`)) AS `handed_off_at`
  FROM `handoff_record`
  WHERE `status` = 'COMPLETED'
  GROUP BY `bag_no`
) handoff ON handoff.`bag_no` = bag.`bag_no`
SET
  bag.`status` = 'DISPATCHED',
  bag.`updated_at` = COALESCE(handoff.`handed_off_at`, NOW())
WHERE bag.`status` <> 'DISPATCHED';

UPDATE `dispatch_batch` batch
JOIN (
  SELECT
    `batch_no`,
    MAX(COALESCE(`updated_at`, `handoff_time`, `created_at`)) AS `handed_off_at`
  FROM `handoff_record`
  WHERE `status` = 'COMPLETED'
  GROUP BY `batch_no`
) handoff ON handoff.`batch_no` = batch.`batch_no`
SET
  batch.`status` = 'HANDED_OFF',
  batch.`updated_at` = COALESCE(handoff.`handed_off_at`, NOW())
WHERE batch.`status` <> 'HANDED_OFF';
