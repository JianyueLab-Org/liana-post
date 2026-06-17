/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80040
 Source Host           : localhost:3306
 Source Schema         : tracking_db

 Target Server Type    : MySQL
 Target Server Version : 80040
 File Encoding         : 65001

 Date: 17/06/2026 08:31:38
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for tracking_event
-- ----------------------------
DROP TABLE IF EXISTS `tracking_event`;
CREATE TABLE `tracking_event`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `waybill_no` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_time` datetime NOT NULL,
  `facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `facility_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `operator_id` bigint NULL DEFAULT NULL,
  `operator_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `payload` json NULL,
  `source_service` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tracking_event_no`(`event_no` ASC) USING BTREE,
  INDEX `idx_tracking_waybill_no`(`waybill_no` ASC) USING BTREE,
  INDEX `idx_tracking_event_type`(`event_type` ASC) USING BTREE,
  INDEX `idx_tracking_event_time`(`event_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tracking_event
-- ----------------------------
INSERT INTO `tracking_event` VALUES (1, 'T202606161709060448175', 'CP526710244LN', 'ACCEPTED', '2026-06-16 17:09:06', 'B1', 'Demo Facility', NULL, NULL, '{}', 'OMS', '2026-06-16 17:09:06');

SET FOREIGN_KEY_CHECKS = 1;
