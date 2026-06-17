/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80040
 Source Host           : localhost:3306
 Source Schema         : oms_db

 Target Server Type    : MySQL
 Target Server Version : 80040
 File Encoding         : 65001

 Date: 17/06/2026 08:32:32
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for country
-- ----------------------------
DROP TABLE IF EXISTS `country`;
CREATE TABLE `country`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `english_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `postal_enabled` tinyint NOT NULL DEFAULT 1,
  `upu_region` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_country_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of country
-- ----------------------------
INSERT INTO `country` VALUES (1, 'LN', '利亚纳', 'Liana', 1, 'OCEANIA', '默认本国示例', '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country` VALUES (2, 'HK', '中国香港', 'Hong Kong', 1, 'ASIA_PACIFIC', 'UPU示例目的国', '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country` VALUES (3, 'JP', '日本', 'Japan', 1, 'ASIA_PACIFIC', 'UPU示例目的国', '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country` VALUES (4, 'US', '美国', 'United States', 1, 'NORTH_AMERICA', 'UPU示例目的国', '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country` VALUES (5, 'DE', '德国', 'Germany', 0, 'EUROPE', '示例：当前未通邮', '2026-06-11 14:46:46', '2026-06-11 14:46:46');

-- ----------------------------
-- Table structure for country_service_type
-- ----------------------------
DROP TABLE IF EXISTS `country_service_type`;
CREATE TABLE `country_service_type`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `country_code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `service_type_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_country_service_type`(`country_code` ASC, `service_type_code` ASC) USING BTREE,
  INDEX `idx_country_service_type_country`(`country_code` ASC) USING BTREE,
  INDEX `idx_country_service_type_service`(`service_type_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 16 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of country_service_type
-- ----------------------------
INSERT INTO `country_service_type` VALUES (1, 'LN', 'AIR', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (2, 'LN', 'SAL', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (3, 'LN', 'SEA', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (4, 'HK', 'AIR', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (5, 'HK', 'SAL', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (6, 'HK', 'SEA', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (7, 'JP', 'AIR', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (8, 'JP', 'SAL', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (9, 'JP', 'SEA', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (10, 'US', 'AIR', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (11, 'US', 'SAL', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (12, 'US', 'SEA', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (13, 'DE', 'AIR', 0, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (14, 'DE', 'SAL', 0, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `country_service_type` VALUES (15, 'DE', 'SEA', 0, '2026-06-11 14:46:46', '2026-06-11 14:46:46');

-- ----------------------------
-- Table structure for mail
-- ----------------------------
DROP TABLE IF EXISTS `mail`;
CREATE TABLE `mail`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `waybill_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `bag_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所属总包/邮袋号 (白天收寄为空，晚上封发时填入)',
  `package_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `mail_type_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `service_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `mail_scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DOMESTIC' COMMENT '邮件范围 DOMESTIC/INTERNATIONAL',
  `dest_country_code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '寄达国代码',
  `sender_id` bigint NOT NULL,
  `recipient_id` bigint NOT NULL,
  `origin_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `current_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `current_slot` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `dest_facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `destination_node` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CREATED',
  `weight_grams` int NULL DEFAULT NULL,
  `declared_value` decimal(12, 2) NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_mail_waybill_no`(`waybill_no` ASC) USING BTREE,
  INDEX `idx_mail_bag_no`(`bag_no` ASC) USING BTREE,
  INDEX `idx_mail_status`(`status` ASC) USING BTREE,
  INDEX `idx_mail_sender_id`(`sender_id` ASC) USING BTREE,
  INDEX `idx_mail_recipient_id`(`recipient_id` ASC) USING BTREE,
  INDEX `idx_mail_current_facility_code`(`current_facility_code` ASC) USING BTREE,
  INDEX `idx_mail_package_id`(`package_id` ASC) USING BTREE,
  INDEX `idx_mail_current_slot`(`current_slot` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 27 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of mail
-- ----------------------------
INSERT INTO `mail` VALUES (1, 'EE123456785LN', 'B202606101738346808619', NULL, 'R', 'STANDARD', 'DOMESTIC', NULL, 1, 1, 'B1', 'B1', NULL, 'C1', NULL, 'DISPATCHED', 120, NULL, '2026-06-10 14:24:45', '2026-06-10 17:38:35');
INSERT INTO `mail` VALUES (2, 'RP979470949LN', 'B202606101432198441907', NULL, 'R', 'STANDARD', 'DOMESTIC', NULL, 2, 2, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-10 14:32:05', '2026-06-10 17:38:10');
INSERT INTO `mail` VALUES (3, 'CP996204603LN', 'B202606101830444117231', NULL, 'R', 'STANDARD', 'DOMESTIC', NULL, 3, 3, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-10 17:37:32', '2026-06-10 18:30:44');
INSERT INTO `mail` VALUES (4, 'CP268251585LN', 'B202606101830444117231', NULL, 'R', 'STANDARD', 'DOMESTIC', NULL, 4, 4, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-10 18:30:33', '2026-06-10 18:30:44');
INSERT INTO `mail` VALUES (5, 'CP412667870LN', 'B202606101830444117231', NULL, 'R', 'STANDARD', 'DOMESTIC', NULL, 5, 5, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-10 18:30:36', '2026-06-10 18:30:44');
INSERT INTO `mail` VALUES (6, 'CP020974363LN', 'B202606101830444117231', NULL, 'R', 'STANDARD', 'DOMESTIC', NULL, 6, 6, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-10 18:30:36', '2026-06-10 18:30:44');
INSERT INTO `mail` VALUES (7, 'CP117677005LN', 'B202606101830444117231', NULL, 'R', 'STANDARD', 'DOMESTIC', NULL, 7, 7, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-10 18:30:37', '2026-06-10 18:30:44');
INSERT INTO `mail` VALUES (8, 'CP716562793LN', 'B202606101830444117231', NULL, 'R', 'STANDARD', 'DOMESTIC', NULL, 8, 8, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-10 18:30:37', '2026-06-10 18:30:44');
INSERT INTO `mail` VALUES (9, 'CP534970949LN', 'B202606141944282829166', NULL, 'E', 'AIR', 'DOMESTIC', NULL, 9, 9, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 19:44:17', '2026-06-14 19:44:28');
INSERT INTO `mail` VALUES (10, 'CP012182190LN', 'B202606141944282829166', NULL, 'E', 'AIR', 'DOMESTIC', NULL, 10, 10, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 19:44:18', '2026-06-14 19:44:28');
INSERT INTO `mail` VALUES (11, 'CP910563765LN', 'B202606141944282829166', NULL, 'E', 'AIR', 'DOMESTIC', NULL, 11, 11, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 19:44:19', '2026-06-14 19:44:28');
INSERT INTO `mail` VALUES (12, 'CP359877856LN', 'B202606141944282829166', NULL, 'E', 'AIR', 'DOMESTIC', NULL, 12, 12, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 19:44:20', '2026-06-14 19:44:28');
INSERT INTO `mail` VALUES (13, 'CP716878104LN', 'B202606142058276486248', NULL, 'R', 'AIR', 'DOMESTIC', NULL, 13, 13, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 20:57:59', '2026-06-14 20:58:28');
INSERT INTO `mail` VALUES (14, 'CP227420660LN', 'B202606142058276486248', NULL, 'R', 'AIR', 'DOMESTIC', NULL, 14, 14, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 20:58:00', '2026-06-14 20:58:28');
INSERT INTO `mail` VALUES (15, 'CP805918012LN', 'B202606142058276486248', NULL, 'R', 'AIR', 'DOMESTIC', NULL, 15, 15, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 20:58:00', '2026-06-14 20:58:28');
INSERT INTO `mail` VALUES (16, 'CP261040131LN', 'B202606142058276486248', NULL, 'R', 'AIR', 'DOMESTIC', NULL, 16, 16, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 20:58:00', '2026-06-14 20:58:28');
INSERT INTO `mail` VALUES (17, 'CP589971586LN', 'B202606142113545173520', NULL, 'E', 'AIR', 'DOMESTIC', NULL, 17, 17, 'B1', 'B1', NULL, NULL, NULL, 'SORTED', 120, NULL, '2026-06-14 21:13:38', '2026-06-14 21:24:08');
INSERT INTO `mail` VALUES (18, 'CP832332893LN', 'B202606142113545173520', NULL, 'E', 'AIR', 'DOMESTIC', NULL, 18, 18, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 21:13:38', '2026-06-14 21:13:55');
INSERT INTO `mail` VALUES (19, 'CP410839784LN', 'B202606142113545173520', NULL, 'E', 'AIR', 'DOMESTIC', NULL, 19, 19, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 21:13:38', '2026-06-14 21:13:55');
INSERT INTO `mail` VALUES (20, 'CP863155717LN', 'B202606142113545173520', NULL, 'E', 'AIR', 'DOMESTIC', NULL, 20, 20, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 21:13:38', '2026-06-14 21:13:55');
INSERT INTO `mail` VALUES (21, 'CP937723240LN', 'B202606142113545173520', NULL, 'E', 'AIR', 'DOMESTIC', NULL, 21, 21, 'B1', 'B1', NULL, NULL, NULL, 'DISPATCHED', 120, NULL, '2026-06-14 21:13:39', '2026-06-14 21:13:55');
INSERT INTO `mail` VALUES (22, 'CP410792509LN', 'B202606142257385910126', NULL, 'R', 'AIR', 'DOMESTIC', NULL, 22, 22, 'B1', 'B1', NULL, NULL, NULL, 'SORTED', 120, NULL, '2026-06-14 22:57:06', '2026-06-14 23:05:17');
INSERT INTO `mail` VALUES (23, 'CP181462670LN', 'B202606142257385910126', NULL, 'R', 'AIR', 'DOMESTIC', NULL, 23, 23, 'B1', 'B1', NULL, NULL, NULL, 'SORTED', 120, NULL, '2026-06-14 22:57:11', '2026-06-14 23:05:18');
INSERT INTO `mail` VALUES (24, 'CP575712238LN', 'B202606151304225858768', 'B202606151304225858768', 'R', 'AIR', 'DOMESTIC', NULL, 24, 24, 'B1', 'B2', NULL, NULL, 'B2', 'DELIVERED', 120, NULL, '2026-06-15 12:29:18', '2026-06-15 14:02:48');
INSERT INTO `mail` VALUES (25, 'CP108284632LN', 'B202606151304225858768', 'B202606151304225858768', 'R', 'AIR', 'DOMESTIC', NULL, 25, 25, 'B1', 'B2', NULL, NULL, 'B2', 'DELIVERED', 120, NULL, '2026-06-15 12:29:23', '2026-06-15 14:02:43');
INSERT INTO `mail` VALUES (26, 'CP349675885LN', 'B202606161644170267241', 'B202606161644170267241', 'R', 'AIR', 'INTERNATIONAL', 'JP', 28, 28, 'B2', 'A2', NULL, NULL, 'JP', 'DISPATCHED', 120, NULL, '2026-06-15 14:19:10', '2026-06-16 16:44:18');

-- ----------------------------
-- Table structure for mail_type
-- ----------------------------
DROP TABLE IF EXISTS `mail_type`;
CREATE TABLE `mail_type`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `requires_signature` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_mail_type_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of mail_type
-- ----------------------------
INSERT INTO `mail_type` VALUES (1, 'C', '包裹', '普通包裹', 0, '2026-06-10 14:24:45', '2026-06-10 14:24:45');
INSERT INTO `mail_type` VALUES (2, 'R', '挂号信', '挂号信邮件', 1, '2026-06-10 14:24:45', '2026-06-10 14:24:45');
INSERT INTO `mail_type` VALUES (3, 'E', 'EMS', '特快专递', 1, '2026-06-10 14:24:45', '2026-06-10 14:24:45');

-- ----------------------------
-- Table structure for recipient
-- ----------------------------
DROP TABLE IF EXISTS `recipient`;
CREATE TABLE `recipient`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `full_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `postcode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `country_code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LN',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_recipient_phone`(`phone` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 29 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of recipient
-- ----------------------------
INSERT INTO `recipient` VALUES (1, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-10 14:24:45', '2026-06-10 14:24:45');
INSERT INTO `recipient` VALUES (2, '1', '1', '1', '1', 'LN', '2026-06-10 14:32:05', '2026-06-10 14:32:05');
INSERT INTO `recipient` VALUES (3, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-10 17:37:32', '2026-06-10 17:37:32');
INSERT INTO `recipient` VALUES (4, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-10 18:30:33', '2026-06-10 18:30:33');
INSERT INTO `recipient` VALUES (5, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-10 18:30:36', '2026-06-10 18:30:36');
INSERT INTO `recipient` VALUES (6, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-10 18:30:36', '2026-06-10 18:30:36');
INSERT INTO `recipient` VALUES (7, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-10 18:30:37', '2026-06-10 18:30:37');
INSERT INTO `recipient` VALUES (8, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-10 18:30:37', '2026-06-10 18:30:37');
INSERT INTO `recipient` VALUES (9, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 19:44:17', '2026-06-14 19:44:17');
INSERT INTO `recipient` VALUES (10, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 19:44:18', '2026-06-14 19:44:18');
INSERT INTO `recipient` VALUES (11, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 19:44:19', '2026-06-14 19:44:19');
INSERT INTO `recipient` VALUES (12, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 19:44:20', '2026-06-14 19:44:20');
INSERT INTO `recipient` VALUES (13, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 20:57:59', '2026-06-14 20:57:59');
INSERT INTO `recipient` VALUES (14, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 20:58:00', '2026-06-14 20:58:00');
INSERT INTO `recipient` VALUES (15, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 20:58:00', '2026-06-14 20:58:00');
INSERT INTO `recipient` VALUES (16, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 20:58:00', '2026-06-14 20:58:00');
INSERT INTO `recipient` VALUES (17, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 21:13:38', '2026-06-14 21:13:38');
INSERT INTO `recipient` VALUES (18, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 21:13:38', '2026-06-14 21:13:38');
INSERT INTO `recipient` VALUES (19, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 21:13:38', '2026-06-14 21:13:38');
INSERT INTO `recipient` VALUES (20, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 21:13:38', '2026-06-14 21:13:38');
INSERT INTO `recipient` VALUES (21, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 21:13:39', '2026-06-14 21:13:39');
INSERT INTO `recipient` VALUES (22, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 22:57:06', '2026-06-14 22:57:06');
INSERT INTO `recipient` VALUES (23, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-14 22:57:11', '2026-06-14 22:57:11');
INSERT INTO `recipient` VALUES (24, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-15 12:29:18', '2026-06-15 12:29:18');
INSERT INTO `recipient` VALUES (25, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-15 12:29:23', '2026-06-15 12:29:23');
INSERT INTO `recipient` VALUES (28, 'Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN', '2026-06-15 14:19:10', '2026-06-15 14:19:10');

-- ----------------------------
-- Table structure for sender
-- ----------------------------
DROP TABLE IF EXISTS `sender`;
CREATE TABLE `sender`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `full_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `id_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `id_number` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `postcode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `country_code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LN',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sender_phone`(`phone` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 29 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sender
-- ----------------------------
INSERT INTO `sender` VALUES (1, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-10 14:24:45', '2026-06-10 14:24:45');
INSERT INTO `sender` VALUES (2, '1', '1', 'NID', '1', '1', '1', 'LN', '2026-06-10 14:32:05', '2026-06-10 14:32:05');
INSERT INTO `sender` VALUES (3, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-10 17:37:32', '2026-06-10 17:37:32');
INSERT INTO `sender` VALUES (4, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-10 18:30:33', '2026-06-10 18:30:33');
INSERT INTO `sender` VALUES (5, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-10 18:30:36', '2026-06-10 18:30:36');
INSERT INTO `sender` VALUES (6, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-10 18:30:36', '2026-06-10 18:30:36');
INSERT INTO `sender` VALUES (7, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-10 18:30:37', '2026-06-10 18:30:37');
INSERT INTO `sender` VALUES (8, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-10 18:30:37', '2026-06-10 18:30:37');
INSERT INTO `sender` VALUES (9, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 19:44:17', '2026-06-14 19:44:17');
INSERT INTO `sender` VALUES (10, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 19:44:18', '2026-06-14 19:44:18');
INSERT INTO `sender` VALUES (11, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 19:44:19', '2026-06-14 19:44:19');
INSERT INTO `sender` VALUES (12, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 19:44:20', '2026-06-14 19:44:20');
INSERT INTO `sender` VALUES (13, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 20:57:59', '2026-06-14 20:57:59');
INSERT INTO `sender` VALUES (14, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 20:58:00', '2026-06-14 20:58:00');
INSERT INTO `sender` VALUES (15, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 20:58:00', '2026-06-14 20:58:00');
INSERT INTO `sender` VALUES (16, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 20:58:00', '2026-06-14 20:58:00');
INSERT INTO `sender` VALUES (17, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 21:13:38', '2026-06-14 21:13:38');
INSERT INTO `sender` VALUES (18, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 21:13:38', '2026-06-14 21:13:38');
INSERT INTO `sender` VALUES (19, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 21:13:38', '2026-06-14 21:13:38');
INSERT INTO `sender` VALUES (20, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 21:13:38', '2026-06-14 21:13:38');
INSERT INTO `sender` VALUES (21, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 21:13:39', '2026-06-14 21:13:39');
INSERT INTO `sender` VALUES (22, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 22:57:06', '2026-06-14 22:57:06');
INSERT INTO `sender` VALUES (23, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-14 22:57:11', '2026-06-14 22:57:11');
INSERT INTO `sender` VALUES (24, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-15 12:29:18', '2026-06-15 12:29:18');
INSERT INTO `sender` VALUES (25, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-15 12:29:23', '2026-06-15 12:29:23');
INSERT INTO `sender` VALUES (28, 'Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN', '2026-06-15 14:19:10', '2026-06-15 14:19:10');

-- ----------------------------
-- Table structure for service_type
-- ----------------------------
DROP TABLE IF EXISTS `service_type`;
CREATE TABLE `service_type`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_service_type_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of service_type
-- ----------------------------
INSERT INTO `service_type` VALUES (1, 'AIR', '航空', '航空运输服务', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `service_type` VALUES (2, 'SAL', 'SAL', '空陆联运服务', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');
INSERT INTO `service_type` VALUES (3, 'SEA', '海运', '海运运输服务', 1, '2026-06-11 14:46:46', '2026-06-11 14:46:46');

SET FOREIGN_KEY_CHECKS = 1;
