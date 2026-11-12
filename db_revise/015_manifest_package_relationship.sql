CREATE TABLE IF NOT EXISTS `sorting_manifest_package` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `manifest_no` VARCHAR(64) NOT NULL,
  `package_no` VARCHAR(64) NOT NULL,
  `expected_seq_no` INT NOT NULL,
  `expected_weight` DECIMAL(12,3) DEFAULT NULL,
  `actual_weight` DECIMAL(12,3) DEFAULT NULL,
  `seal_no` VARCHAR(64) DEFAULT NULL,
  `item_status` VARCHAR(16) NOT NULL DEFAULT 'EXPECTED',
  `received_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_manifest_package` (`manifest_no`, `package_no`),
  KEY `idx_manifest_package_package` (`package_no`, `item_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `sorting_manifest_package`
  (`manifest_no`, `package_no`, `expected_seq_no`, `item_status`)
SELECT grouped.`manifest_no`, grouped.`package_no`,
       ROW_NUMBER() OVER (PARTITION BY grouped.`manifest_no` ORDER BY grouped.`package_no`),
       CASE WHEN package_record.`package_status` IN ('RECEIVED', 'OPENING', 'OPENED') THEN 'RECEIVED' ELSE 'EXPECTED' END
FROM (
  SELECT `manifest_no`, `expected_package_no` AS `package_no`
  FROM `sorting_manifest_item`
  WHERE `expected_package_no` IS NOT NULL AND `expected_package_no` <> ''
  GROUP BY `manifest_no`, `expected_package_no`
) grouped
LEFT JOIN `sorting_total_package` package_record
  ON package_record.`package_no` = grouped.`package_no`;

UPDATE `sorting_total_package` package_record
JOIN `sorting_manifest_package` manifest_package
  ON manifest_package.`package_no` = package_record.`package_no`
SET package_record.`manifest_no` = manifest_package.`manifest_no`
WHERE package_record.`manifest_no` IS NULL OR package_record.`manifest_no` = '';

INSERT IGNORE INTO `sorting_manifest_package`
  (`manifest_no`, `package_no`, `expected_seq_no`, `item_status`)
SELECT manifest.`manifest_no`, manifest.`manifest_no`, 1,
       CASE WHEN package_record.`package_status` IN ('RECEIVED', 'OPENING', 'OPENED') THEN 'RECEIVED' ELSE 'EXPECTED' END
FROM `sorting_manifest` manifest
LEFT JOIN `sorting_total_package` package_record
  ON package_record.`package_no` = manifest.`manifest_no`
WHERE manifest.`expected_package_qty` = 1
  AND package_record.`package_no` IS NOT NULL;

UPDATE `sorting_manifest` manifest
SET manifest.`manifest_status` = 'PREALERT'
WHERE manifest.`manifest_status` = 'RECEIVED'
  AND NOT EXISTS (
    SELECT 1 FROM `sorting_total_package` package_record
    WHERE package_record.`manifest_no` = manifest.`manifest_no`
      AND package_record.`package_status` IN ('RECEIVED', 'OPENING', 'OPENED')
  );
