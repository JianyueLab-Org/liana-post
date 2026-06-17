/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80040
 Source Host           : localhost:3306
 Source Schema         : dispatch_db

 Target Server Type    : MySQL
 Target Server Version : 80040
 File Encoding         : 65001

 Date: 17/06/2026 08:32:55
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for dispatch_bag
-- ----------------------------
DROP TABLE IF EXISTS `dispatch_bag`;
CREATE TABLE `dispatch_bag`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bag_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `origin_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `destination_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `route_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `transport_task_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联运输任务编码',
  `mail_no_list` json NOT NULL,
  `mail_type_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CREATED',
  `mail_count` int NOT NULL DEFAULT 0,
  `total_weight_grams` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_dispatch_bag_no`(`bag_no` ASC) USING BTREE,
  INDEX `idx_dispatch_bag_status`(`status` ASC) USING BTREE,
  INDEX `idx_dispatch_bag_origin`(`origin_facility_code` ASC) USING BTREE,
  INDEX `idx_dispatch_bag_destination`(`destination_facility_code` ASC) USING BTREE,
  INDEX `idx_dispatch_bag_transport_task_code`(`transport_task_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dispatch_bag
-- ----------------------------
INSERT INTO `dispatch_bag` VALUES (1, 'B202606090001', 'B1', 'C1', 'B1-C1-TRUCK', NULL, '[\"LYN0000000001LN\"]', 'R', 'DISPATCHED', 1, 120, '2026-06-14 19:58:03', '2026-06-14 19:58:03');
INSERT INTO `dispatch_bag` VALUES (2, 'B202606090002', 'B1', 'A1', 'B1-C1-TRUCK', NULL, '[\"LYN0000000002LN\"]', 'C', 'DISPATCHED', 1, 130, '2026-06-14 21:00:57', '2026-06-14 21:00:57');
INSERT INTO `dispatch_bag` VALUES (3, 'B202606101738346808619', 'B1', 'C1', 'B1-C1-TRUCK', NULL, '[\"EE123456785LN\"]', 'R', 'REVIEWED', 1, 100, '2026-06-10 17:38:35', '2026-06-10 17:38:35');
INSERT INTO `dispatch_bag` VALUES (4, 'B202606101830444117231', 'B1', '', NULL, NULL, '[\"CP996204603LN\", \"CP268251585LN\", \"CP412667870LN\", \"CP020974363LN\", \"CP117677005LN\", \"CP716562793LN\"]', 'R', 'REVIEWED', 6, 600, '2026-06-10 18:30:45', '2026-06-10 18:30:45');
INSERT INTO `dispatch_bag` VALUES (5, 'B202606141944282829166', 'B1', '', NULL, NULL, '[\"CP534970949LN\", \"CP012182190LN\", \"CP910563765LN\", \"CP359877856LN\"]', 'E', 'REVIEWED', 4, 400, '2026-06-14 19:44:29', '2026-06-14 19:44:29');
INSERT INTO `dispatch_bag` VALUES (6, 'B202606142058276486248', 'B1', '', NULL, NULL, '[\"CP716878104LN\", \"CP227420660LN\", \"CP805918012LN\", \"CP261040131LN\"]', 'R', 'REVIEWED', 4, 400, '2026-06-14 20:58:28', '2026-06-14 20:58:28');
INSERT INTO `dispatch_bag` VALUES (7, 'B202606142113545173520', 'B1', '', NULL, NULL, '[\"CP589971586LN\", \"CP832332893LN\", \"CP410839784LN\", \"CP863155717LN\", \"CP937723240LN\"]', 'E', 'DISPATCHED', 5, 500, '2026-06-14 21:23:39', '2026-06-14 21:23:39');
INSERT INTO `dispatch_bag` VALUES (8, 'B202606142257385910126', 'B1', '', NULL, NULL, '[\"CP410792509LN\", \"CP181462670LN\"]', 'R', 'DISPATCHED', 2, 200, '2026-06-14 23:00:50', '2026-06-14 23:00:50');
INSERT INTO `dispatch_bag` VALUES (9, 'B202606151239221266281', 'B1', '', NULL, NULL, '[\"CP575712238LN\", \"CP108284632LN\"]', 'R', 'DISPATCHED', 2, 200, '2026-06-15 12:40:10', '2026-06-15 12:40:10');
INSERT INTO `dispatch_bag` VALUES (10, 'B202606151419192235333', 'B2', '', NULL, NULL, '[\"CP349675885LN\"]', 'R', 'DISPATCHED', 1, 100, '2026-06-15 14:23:13', '2026-06-15 14:23:13');

-- ----------------------------
-- Table structure for dispatch_batch
-- ----------------------------
DROP TABLE IF EXISTS `dispatch_batch`;
CREATE TABLE `dispatch_batch`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `bag_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `route_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `approved_by` bigint NULL DEFAULT NULL,
  `approved_at` datetime NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_dispatch_batch_no`(`batch_no` ASC) USING BTREE,
  INDEX `idx_dispatch_batch_bag_no`(`bag_no` ASC) USING BTREE,
  INDEX `idx_dispatch_batch_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dispatch_batch
-- ----------------------------
INSERT INTO `dispatch_batch` VALUES (1, 'D202606090001', 'B202606090001', 'B1-C1-TRUCK', 'APPROVED', 5, '2026-06-14 20:57:40', '2026-06-14 20:57:40', '2026-06-14 20:57:40');
INSERT INTO `dispatch_batch` VALUES (2, 'D202606142100567853040', 'B202606090002', 'B1-C1-TRUCK', 'HANDED_OFF', NULL, NULL, '2026-06-14 21:00:57', '2026-06-14 21:00:57');
INSERT INTO `dispatch_batch` VALUES (3, 'D202606142113555816192', 'B202606142113545173520', NULL, 'HANDED_OFF', NULL, NULL, '2026-06-14 21:14:02', '2026-06-14 21:14:02');
INSERT INTO `dispatch_batch` VALUES (4, 'D202606142257393198422', 'B202606142257385910126', NULL, 'APPROVED', 1, '2026-06-14 23:03:20', '2026-06-14 23:03:20', '2026-06-14 23:03:20');
INSERT INTO `dispatch_batch` VALUES (5, 'D202606151239228401947', 'B202606151239221266281', NULL, 'HANDED_OFF', NULL, NULL, '2026-06-15 12:39:37', '2026-06-15 12:39:37');
INSERT INTO `dispatch_batch` VALUES (6, 'D202606151419194817070', 'B202606151419192235333', NULL, 'HANDED_OFF', NULL, NULL, '2026-06-15 14:20:24', '2026-06-15 14:20:24');

-- ----------------------------
-- Table structure for dispatch_transport_task_link
-- ----------------------------
DROP TABLE IF EXISTS `dispatch_transport_task_link`;
CREATE TABLE `dispatch_transport_task_link`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dispatch_bag_id` bigint NOT NULL,
  `transport_task_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_dispatch_transport_task_link_bag`(`dispatch_bag_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_dispatch_transport_task_link_task`(`transport_task_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dispatch_transport_task_link
-- ----------------------------

-- ----------------------------
-- Table structure for handoff_record
-- ----------------------------
DROP TABLE IF EXISTS `handoff_record`;
CREATE TABLE `handoff_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `handoff_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `bag_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `batch_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `from_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `to_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `handoff_time` datetime NOT NULL,
  `receiver_id` bigint NULL DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_handoff_no`(`handoff_no` ASC) USING BTREE,
  INDEX `idx_handoff_batch_no`(`batch_no` ASC) USING BTREE,
  INDEX `idx_handoff_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of handoff_record
-- ----------------------------
INSERT INTO `handoff_record` VALUES (1, 'H202606090001', 'B202606090001', 'D202606090001', 'B1', 'C1', '2026-06-09 08:00:00', NULL, 'PENDING', '2026-06-10 17:37:13', '2026-06-10 17:37:13');
INSERT INTO `handoff_record` VALUES (2, 'H202606141958033361059', 'B202606090001', 'D202606090001', 'B1', 'A1', '2026-06-14 19:58:03', 5, 'COMPLETED', '2026-06-14 19:58:03', '2026-06-14 19:58:03');
INSERT INTO `handoff_record` VALUES (3, 'H202606142100568074534', 'B202606090002', 'D202606142100567853040', 'B1', 'A1', '2026-06-14 21:00:57', 5, 'COMPLETED', '2026-06-14 21:00:57', '2026-06-14 21:00:57');
INSERT INTO `handoff_record` VALUES (4, 'H202606142114021144245', 'B202606142113545173520', 'D202606142113555816192', 'B1', 'A1', '2026-06-14 21:14:02', 5, 'COMPLETED', '2026-06-14 21:14:02', '2026-06-14 21:14:02');
INSERT INTO `handoff_record` VALUES (5, 'H202606142258528185568', 'B202606142257385910126', 'D202606142257393198422', 'B1', 'A1', '2026-06-14 22:58:53', 5, 'COMPLETED', '2026-06-14 22:58:53', '2026-06-14 22:58:53');
INSERT INTO `handoff_record` VALUES (6, 'H202606151239370998305', 'B202606151239221266281', 'D202606151239228401947', 'B1', 'A1', '2026-06-15 12:39:37', 1, 'COMPLETED', '2026-06-15 12:39:37', '2026-06-15 12:39:37');
INSERT INTO `handoff_record` VALUES (7, 'H202606151420236470404', 'B202606151419192235333', 'D202606151419194817070', 'B2', 'A1', '2026-06-15 14:20:24', 7, 'COMPLETED', '2026-06-15 14:20:24', '2026-06-15 14:20:24');

-- ----------------------------
-- Table structure for route_rule
-- ----------------------------
DROP TABLE IF EXISTS `route_rule`;
CREATE TABLE `route_rule`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `priority_level` int NOT NULL DEFAULT 0,
  `transport_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_route_rule_code`(`rule_code` ASC) USING BTREE,
  INDEX `idx_route_rule_source`(`source_facility_code` ASC) USING BTREE,
  INDEX `idx_route_rule_target`(`target_facility_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of route_rule
-- ----------------------------
INSERT INTO `route_rule` VALUES (1, 'B1-C1-TRUCK', 'B1', 'C1', 1, 'TRUCK', 1, '2026-06-10 17:37:13', '2026-06-10 17:37:13');
INSERT INTO `route_rule` VALUES (2, 'B1-C1-AIR', 'B1', 'C1', 2, 'AIR', 1, '2026-06-10 17:37:13', '2026-06-10 17:37:13');

SET FOREIGN_KEY_CHECKS = 1;
