-- Audited empty-schema baseline. No legacy business rows. UTC DATETIME(3).
-- Provenance and retained legacy contracts: database/inventory.md

CREATE TABLE `dispatch_bag`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bag_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `origin_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `destination_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `route_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `facility_route_code` VARCHAR(64) DEFAULT NULL,
  `transport_task_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联运输任务编码',
  `mail_no_list` json NOT NULL,
  `mail_type_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CREATED',
  `mail_count` int NOT NULL DEFAULT 0,
  `total_weight_grams` int NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY `idx_dispatch_bag_facility_edge` (`facility_route_code`),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_dispatch_bag_no`(`bag_no` ASC) USING BTREE,
  INDEX `idx_dispatch_bag_status`(`status` ASC) USING BTREE,
  INDEX `idx_dispatch_bag_origin`(`origin_facility_code` ASC) USING BTREE,
  INDEX `idx_dispatch_bag_destination`(`destination_facility_code` ASC) USING BTREE,
  INDEX `idx_dispatch_bag_transport_task_code`(`transport_task_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `dispatch_batch`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `bag_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `route_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `approved_by` bigint NULL DEFAULT NULL,
  `approved_at` DATETIME(3) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_dispatch_batch_no`(`batch_no` ASC) USING BTREE,
  INDEX `idx_dispatch_batch_bag_no`(`bag_no` ASC) USING BTREE,
  INDEX `idx_dispatch_batch_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `dispatch_batch_bag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(32) NOT NULL,
  `bag_no` varchar(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_batch_bag` (`bag_no`),
  KEY `idx_dispatch_batch_bag_batch` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `dispatch_transport_task_link`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dispatch_bag_id` bigint NOT NULL,
  `transport_task_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_dispatch_transport_task_link_bag`(`dispatch_bag_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_dispatch_transport_task_link_task`(`transport_task_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `handoff_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `handoff_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `bag_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `batch_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `from_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `to_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `handoff_time` DATETIME(3) NOT NULL,
  `receiver_id` bigint NULL DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY `idx_handoff_bag_no` (`bag_no`, `status`),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_handoff_no`(`handoff_no` ASC) USING BTREE,
  INDEX `idx_handoff_batch_no`(`batch_no` ASC) USING BTREE,
  INDEX `idx_handoff_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `route_rule`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `route_scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DOMESTIC',
  `dest_country_code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `export_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `priority_level` int NOT NULL DEFAULT 0,
  `transport_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_route_rule_code`(`rule_code` ASC) USING BTREE,
  INDEX `idx_route_rule_source`(`source_facility_code` ASC) USING BTREE,
  INDEX `idx_route_rule_target`(`target_facility_code` ASC) USING BTREE,
  INDEX `idx_route_rule_scope_country`(`source_facility_code` ASC, `route_scope` ASC, `dest_country_code` ASC, `enabled` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE demo_seed_history (
  seed_version VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL,
  applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
