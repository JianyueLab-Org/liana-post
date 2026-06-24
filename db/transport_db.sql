/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80040
 Source Host           : localhost:3306
 Source Schema         : transport_db

 Target Server Type    : MySQL
 Target Server Version : 80040
 File Encoding         : 65001

 Date: 17/06/2026 08:31:28
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for transport_asset
-- ----------------------------
DROP TABLE IF EXISTS `transport_asset`;
CREATE TABLE `transport_asset`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `capacity` decimal(12, 2) NULL DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_transport_asset_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of transport_asset
-- ----------------------------
INSERT INTO `transport_asset` VALUES (1, 'T-001', 'Post Truck 12', 'TRUCK', 80.00, 'AVAILABLE', '2026-06-11 09:50:55', '2026-06-11 09:50:55');
INSERT INTO `transport_asset` VALUES (2, '1', 'faw01', 'TRUCK', 10.00, 'AVAILABLE', '2026-06-11 09:51:32', '2026-06-11 09:51:32');

-- ----------------------------
-- Table structure for transport_route
-- ----------------------------
DROP TABLE IF EXISTS `transport_route`;
CREATE TABLE `transport_route`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `route_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `origin_facility_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `destination_facility_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `transport_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `estimated_hours` decimal(10, 2) NULL DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_transport_route_code`(`route_code` ASC) USING BTREE,
  INDEX `idx_transport_route_origin`(`origin_facility_id` ASC) USING BTREE,
  INDEX `idx_transport_route_destination`(`destination_facility_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of transport_route
-- ----------------------------

-- ----------------------------
-- Table structure for transport_schedule
-- ----------------------------
DROP TABLE IF EXISTS `transport_schedule`;
CREATE TABLE `transport_schedule`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `schedule_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_id` bigint NOT NULL,
  `route_id` bigint NOT NULL,
  `departure_time` datetime NOT NULL,
  `arrival_time` datetime NOT NULL,
  `weekday` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_transport_schedule_code`(`schedule_code` ASC) USING BTREE,
  INDEX `idx_transport_schedule_asset`(`asset_id` ASC) USING BTREE,
  INDEX `idx_transport_schedule_route`(`route_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of transport_schedule
-- ----------------------------

-- ----------------------------
-- Table structure for transport_task
-- ----------------------------
DROP TABLE IF EXISTS `transport_task`;
CREATE TABLE `transport_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dispatch_bag_id` bigint NULL DEFAULT NULL,
  `asset_id` bigint NULL DEFAULT NULL,
  `route_id` bigint NULL DEFAULT NULL,
  `schedule_id` bigint NULL DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_transport_task_code`(`task_code` ASC) USING BTREE,
  INDEX `idx_transport_task_dispatch_bag`(`dispatch_bag_id` ASC) USING BTREE,
  INDEX `idx_transport_task_asset`(`asset_id` ASC) USING BTREE,
  INDEX `idx_transport_task_route`(`route_id` ASC) USING BTREE,
  INDEX `idx_transport_task_schedule`(`schedule_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of transport_task
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
