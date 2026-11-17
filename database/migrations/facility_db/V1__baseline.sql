-- Audited empty-schema baseline. No legacy business rows. UTC DATETIME(3).
-- Provenance and retained legacy contracts: database/inventory.md

CREATE TABLE `facility`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `type_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '上级设施节点编码 (如小岛网点的上级是区域枢纽)',
  `country_code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LN',
  `province` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `city` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `latitude` decimal(10, 7) NULL DEFAULT NULL,
  `longitude` decimal(10, 7) NULL DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_facility_code`(`facility_code` ASC) USING BTREE,
  INDEX `idx_facility_type_code`(`type_code` ASC) USING BTREE,
  INDEX `idx_parent_facility_code`(`parent_facility_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `facility_route`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `route_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `origin_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `destination_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `transport_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `transport_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '运输类型 SEA/AIR/LAND',
  `distance_km` decimal(10, 2) NULL DEFAULT NULL,
  `estimated_hours` decimal(10, 2) NULL DEFAULT NULL,
  `priority_level` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_facility_route_code`(`route_code` ASC) USING BTREE,
  INDEX `idx_route_origin`(`origin_facility_code` ASC) USING BTREE,
  INDEX `idx_route_destination`(`destination_facility_code` ASC) USING BTREE,
  INDEX `idx_facility_route_transport_type`(`transport_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `facility_type`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_facility_type_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE demo_postal_region (
  region_code VARCHAR(8) NOT NULL,
  region_name VARCHAR(64) NOT NULL,
  postcode_prefix CHAR(2) NOT NULL,
  center_facility_code VARCHAR(64) NOT NULL,
  inhabited_islands SMALLINT NOT NULL,
  coverage_description VARCHAR(255) NOT NULL,
  PRIMARY KEY (region_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE demo_postal_island (
  island_code VARCHAR(32) NOT NULL,
  region_code VARCHAR(8) NOT NULL,
  island_name VARCHAR(64) NOT NULL,
  postcode_range VARCHAR(32) NOT NULL,
  island_type VARCHAR(32) NOT NULL,
  map_x SMALLINT NOT NULL DEFAULT 0,
  map_y SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (island_code),
  KEY idx_demo_postal_island_region (region_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE demo_facility_profile (
  facility_code VARCHAR(64) NOT NULL,
  island_code VARCHAR(32) NOT NULL,
  postcode_coverage VARCHAR(128) NOT NULL,
  service_coverage VARCHAR(255) NOT NULL,
  capacity_description VARCHAR(128) NOT NULL,
  opening_hours VARCHAR(128) NOT NULL,
  map_x SMALLINT NOT NULL,
  map_y SMALLINT NOT NULL,
  PRIMARY KEY (facility_code),
  KEY idx_demo_facility_profile_island (island_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE demo_route_profile (
  route_code VARCHAR(64) NOT NULL,
  frequency_description VARCHAR(128) NOT NULL,
  operating_note VARCHAR(255) NOT NULL,
  PRIMARY KEY (route_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE demo_seed_history (
  seed_version VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL,
  applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
