/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80040
 Source Host           : localhost:3306
 Source Schema         : facility_db

 Target Server Type    : MySQL
 Target Server Version : 80040
 File Encoding         : 65001

 Date: 17/06/2026 08:32:45
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for facility
-- ----------------------------
DROP TABLE IF EXISTS `facility`;
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_facility_code`(`facility_code` ASC) USING BTREE,
  INDEX `idx_facility_type_code`(`type_code` ASC) USING BTREE,
  INDEX `idx_parent_facility_code`(`parent_facility_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of facility
-- ----------------------------
INSERT INTO `facility` VALUES (1, 'A1', 'Liana Prime', 'TRANSFER_CENTER', NULL, 'LN', 'Liana Province', 'Liana City', 'A1主枢纽', NULL, NULL, 1, '2026-06-10 11:57:23', '2026-06-10 11:57:23');
INSERT INTO `facility` VALUES (2, 'B1', 'Namoa Post Office', 'POST_OFFICE', 'A1', 'LN', 'Liana Province', 'Liana City', 'B1支局', NULL, NULL, 1, '2026-06-10 11:57:23', '2026-06-10 11:57:23');
INSERT INTO `facility` VALUES (3, 'B2', 'Taviri Post Office', 'POST_OFFICE', 'A1', 'LN', 'Liana Province', 'Liana City', 'B2支局', NULL, NULL, 1, '2026-06-10 11:57:23', '2026-06-10 11:57:23');
INSERT INTO `facility` VALUES (4, 'B3', 'Kelea Post Office', 'POST_OFFICE', 'A1', 'LN', 'Liana Province', 'Liana City', 'B3支局', NULL, NULL, 1, '2026-06-10 11:57:23', '2026-06-10 11:57:23');
INSERT INTO `facility` VALUES (5, 'C1', 'Oro Island Office', 'POST_OFFICE', 'B1', 'LN', 'Liana Province', 'Liana City', 'C1岛屿网点', NULL, NULL, 1, '2026-06-10 11:57:23', '2026-06-10 11:57:23');
INSERT INTO `facility` VALUES (6, 'C2', 'Miri Island Office', 'POST_OFFICE', 'B2', 'LN', 'Liana Province', 'Liana City', 'C2岛屿网点', NULL, NULL, 1, '2026-06-10 11:57:23', '2026-06-10 11:57:23');
INSERT INTO `facility` VALUES (7, 'C3', 'Sela Island Office', 'POST_OFFICE', 'B3', 'LN', 'Liana Province', 'Liana City', 'C3岛屿网点', NULL, NULL, 1, '2026-06-10 11:57:23', '2026-06-10 11:57:23');
INSERT INTO `facility` VALUES (8, 'A2', 'Liana-Exchange', 'INTERNATIONAL_GATEWAY', NULL, 'LN', 'A', 'B', 'C', 115.0000000, 23.2600000, 1, '2026-06-15 14:31:10', '2026-06-15 14:31:10');

-- ----------------------------
-- Table structure for facility_route
-- ----------------------------
DROP TABLE IF EXISTS `facility_route`;
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_facility_route_code`(`route_code` ASC) USING BTREE,
  INDEX `idx_route_origin`(`origin_facility_code` ASC) USING BTREE,
  INDEX `idx_route_destination`(`destination_facility_code` ASC) USING BTREE,
  INDEX `idx_facility_route_transport_type`(`transport_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of facility_route
-- ----------------------------
INSERT INTO `facility_route` VALUES (1, 'R-A1-B1', 'A1', 'B1', 'LAND', 'LAND', NULL, NULL, 0, 1, '2026-06-10 11:57:23', '2026-06-11 09:49:11');
INSERT INTO `facility_route` VALUES (2, 'R-A1-B2', 'A1', 'B2', 'LAND', 'LAND', NULL, NULL, 0, 1, '2026-06-10 11:57:23', '2026-06-11 09:49:11');
INSERT INTO `facility_route` VALUES (3, 'R-A1-B3', 'A1', 'B3', 'LAND', 'LAND', NULL, NULL, 0, 1, '2026-06-10 11:57:23', '2026-06-11 09:49:11');
INSERT INTO `facility_route` VALUES (4, 'test', 'B1', 'A1', 'LAND', 'LAND', NULL, NULL, 0, 1, '2026-06-10 18:43:35', '2026-06-11 09:49:11');
INSERT INTO `facility_route` VALUES (5, 'B2A1', 'B2', 'A1', 'LAND', NULL, NULL, NULL, 0, 1, '2026-06-15 14:20:16', '2026-06-15 14:20:16');
INSERT INTO `facility_route` VALUES (6, 'LNUS', 'A2', 'US', 'SEA', NULL, NULL, NULL, 0, 1, '2026-06-15 15:07:11', '2026-06-15 15:07:11');

-- ----------------------------
-- Table structure for facility_type
-- ----------------------------
DROP TABLE IF EXISTS `facility_type`;
CREATE TABLE `facility_type`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_facility_type_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of facility_type
-- ----------------------------
INSERT INTO `facility_type` VALUES (1, 'POST_OFFICE', '邮局', '基层邮政网点', '2026-06-10 11:57:23', '2026-06-10 11:57:23');
INSERT INTO `facility_type` VALUES (2, 'TRANSFER_CENTER', '转运中心', '邮件集散与转运中心', '2026-06-10 11:57:23', '2026-06-10 11:57:23');
INSERT INTO `facility_type` VALUES (3, 'INTERNATIONAL_GATEWAY', '国际交换局', '国际邮件交换节点', '2026-06-10 11:57:23', '2026-06-10 11:57:23');
INSERT INTO `facility_type` VALUES (4, 'AIR_HUB', '航空节点', '空运中转节点', '2026-06-10 11:57:23', '2026-06-10 11:57:23');
INSERT INTO `facility_type` VALUES (5, 'SEA_HUB', '海运节点', '海运中转节点', '2026-06-10 11:57:23', '2026-06-10 11:57:23');

SET FOREIGN_KEY_CHECKS = 1;
