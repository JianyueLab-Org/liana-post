-- Audited empty-schema baseline. No legacy business rows. UTC DATETIME(3).
-- Provenance and retained legacy contracts: database/inventory.md

CREATE TABLE `tracking_event`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `waybill_no` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_time` DATETIME(3) NOT NULL,
  `facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `facility_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `operator_id` bigint NULL DEFAULT NULL,
  `operator_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `payload` json NULL,
  `source_service` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tracking_event_no`(`event_no` ASC) USING BTREE,
  INDEX `idx_tracking_waybill_no`(`waybill_no` ASC) USING BTREE,
  INDEX `idx_tracking_event_type`(`event_type` ASC) USING BTREE,
  INDEX `idx_tracking_event_time`(`event_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE demo_seed_history (
  seed_version VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL,
  applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
