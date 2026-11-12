USE `sorting_db`;

UPDATE `sorting_manifest` manifest
JOIN (
  SELECT
    `manifest_no`,
    MAX(COALESCE(`received_at`, `updated_at`, `created_at`)) AS `checked_at`
  FROM `sorting_total_package`
  WHERE `manifest_no` IS NOT NULL
    AND `manifest_no` <> ''
    AND `package_status` IN ('RECEIVED', 'OPENING', 'OPENED')
  GROUP BY `manifest_no`
) checked_manifest ON checked_manifest.`manifest_no` = manifest.`manifest_no`
SET
  manifest.`manifest_status` = 'CHECKED',
  manifest.`updated_at` = COALESCE(checked_manifest.`checked_at`, NOW())
WHERE manifest.`manifest_status` <> 'CHECKED';
