/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80040
 Source Host           : localhost:3306
 Source Schema         : auth_db

 Target Server Type    : MySQL
 Target Server Version : 80040
 File Encoding         : 65001

 Date: 12/06/2026 15:44:40
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for permission
-- ----------------------------
DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_permission_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of permission
-- ----------------------------
INSERT INTO `permission` VALUES (1, 'MAIL_CREATE', '閭欢鏀跺瘎', '閭欢鏀跺瘎', 1, '2026-06-10 09:56:23', '2026-06-10 09:56:23');
INSERT INTO `permission` VALUES (2, 'MAIL_QUERY', '閭欢鏌ヨ', '閭欢鏌ヨ', 1, '2026-06-10 09:56:23', '2026-06-10 09:56:23');
INSERT INTO `permission` VALUES (3, 'TRACK_QUERY', '杞ㄨ抗鏌ヨ', '杞ㄨ抗鏌ヨ', 1, '2026-06-10 09:56:23', '2026-06-10 09:56:23');
INSERT INTO `permission` VALUES (4, 'TOKEN_ISSUE', 'Token绛惧彂', 'Token绛惧彂', 1, '2026-06-10 09:56:23', '2026-06-10 09:56:23');
INSERT INTO `permission` VALUES (5, 'USER_ADMIN', '鐢ㄦ埛绠＄悊', '鐢ㄦ埛绠＄悊', 1, '2026-06-10 09:56:23', '2026-06-10 09:56:23');
INSERT INTO `permission` VALUES (6, 'ROLE_ADMIN', '瑙掕壊绠＄悊', '瑙掕壊绠＄悊', 1, '2026-06-10 09:56:23', '2026-06-10 09:56:23');
INSERT INTO `permission` VALUES (7, 'DISPATCH_BAG_CREATE', '鎬诲寘鍒涘缓', '鎬诲寘鍒涘缓', 1, '2026-06-10 09:56:23', '2026-06-10 09:56:23');
INSERT INTO `permission` VALUES (8, 'DISPATCH_BAG_HANDOFF', '鎬诲寘浜ゆ帴', '鎬诲寘浜ゆ帴', 1, '2026-06-10 09:56:23', '2026-06-10 09:56:23');

-- ----------------------------
-- Table structure for role
-- ----------------------------
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of role
-- ----------------------------
INSERT INTO `role` VALUES (1, 'CLERK', '邮局营业员', '邮局营业员', 1, '2026-06-10 09:59:51', '2026-06-10 09:59:51');
INSERT INTO `role` VALUES (2, 'MANAGER', '邮局经理', '邮局经理', 1, '2026-06-10 09:59:51', '2026-06-10 09:59:51');
INSERT INTO `role` VALUES (3, 'SORTER', '转运中心操作员', '转运中心操作员', 1, '2026-06-10 09:59:51', '2026-06-10 09:59:51');
INSERT INTO `role` VALUES (4, 'ADMIN', '系统管理员', '系统管理员', 1, '2026-06-10 09:59:51', '2026-06-10 09:59:51');

-- ----------------------------
-- Table structure for role_permission
-- ----------------------------
DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_permission`(`role_id` ASC, `permission_id` ASC) USING BTREE,
  INDEX `idx_role_permission_permission_id`(`permission_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 22 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of role_permission
-- ----------------------------
INSERT INTO `role_permission` VALUES (1, 1, 1, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (2, 1, 2, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (3, 1, 3, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (4, 1, 7, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (5, 1, 8, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (6, 2, 1, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (7, 2, 2, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (8, 2, 3, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (9, 2, 7, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (10, 2, 8, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (11, 3, 2, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (12, 3, 3, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (13, 3, 7, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (14, 3, 8, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (15, 4, 1, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (16, 4, 2, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (17, 4, 3, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (18, 4, 4, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (19, 4, 5, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (20, 4, 6, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (21, 4, 7, '2026-06-10 09:56:23');
INSERT INTO `role_permission` VALUES (22, 4, 8, '2026-06-10 09:56:23');

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `facility_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属邮政网点编码 (如: B1, C1)',
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `last_login_at` datetime NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `uk_user_phone`(`phone` ASC) USING BTREE,
  UNIQUE INDEX `uk_user_email`(`email` ASC) USING BTREE,
  INDEX `idx_user_facility`(`facility_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES (1, 'clerk001', '$2a$10$mZClYUVENB7kQNGfNmNWZOID5hmszKZbDdxKXbh.lntNaVOyJM.z6', 'Namoa Clerk', 'B1', '13800000001', 'clerk001@liana.post', 1, NULL, '2026-06-10 09:56:23', '2026-06-10 17:40:55');
INSERT INTO `user` VALUES (2, 'manager001', '$2a$10$wX6zY2Zqk8YgNq2h4XvY5e0mQ0X4uX3x9W0pK2d1qZx1vY5N9r9aG', 'Namoa Manager', 'B1', '13800000002', 'manager001@liana.post', 1, NULL, '2026-06-10 09:56:23', '2026-06-10 09:56:23');
INSERT INTO `user` VALUES (3, 'sorter001', '$2a$10$kOcCoiaTvo7Cc.sO0vJaS.Bu30C5eNZwBc9IhYwsjp/yUy937uDw6', 'Transfer Sorter', 'A1', '13800000003', 'sorter001@liana.post', 1, NULL, '2026-06-10 09:56:23', '2026-06-11 10:00:31');
INSERT INTO `user` VALUES (4, 'admin001', '$2a$10$wX6zY2Zqk8YgNq2h4XvY5e0mQ0X4uX3x9W0pK2d1qZx1vY5N9r9aG', 'System Admin', 'A1', '13800000004', 'admin001@liana.post', 1, NULL, '2026-06-10 09:56:23', '2026-06-10 09:56:23');
INSERT INTO `user` VALUES (5, 'testclerk', '$2a$10$lrWRlZJiBl38w6A5RWUT3.98VoB9toIQ36y1qY9G3B9M29k56ELka', 'Test Clerk', 'B1', NULL, NULL, 1, NULL, '2026-06-10 09:59:51', '2026-06-10 09:59:51');
INSERT INTO `user` VALUES (6, 'testadmin', '$2a$10$NgVNOqa0DYUlSd1zew.vR.Q9JC4uhsrioVMpOOw9Rtm7YbSl4lmvW', 'Test Admin', 'A1', NULL, NULL, 1, NULL, '2026-06-10 09:59:51', '2026-06-10 09:59:51');

-- ----------------------------
-- Table structure for user_role
-- ----------------------------
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_role`(`user_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_user_role_role_id`(`role_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_role
-- ----------------------------
INSERT INTO `user_role` VALUES (1, 1, 1, '2026-06-10 09:56:23');
INSERT INTO `user_role` VALUES (2, 2, 2, '2026-06-10 09:56:23');
INSERT INTO `user_role` VALUES (3, 3, 3, '2026-06-10 09:56:23');
INSERT INTO `user_role` VALUES (4, 4, 4, '2026-06-10 09:56:23');
INSERT INTO `user_role` VALUES (5, 5, 1, '2026-06-10 09:59:51');
INSERT INTO `user_role` VALUES (6, 6, 4, '2026-06-10 09:59:51');

SET FOREIGN_KEY_CHECKS = 1;
