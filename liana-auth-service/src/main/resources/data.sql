INSERT INTO `role` (`code`, `name`, `description`, `status`) VALUES
('CLERK', '钀ヤ笟鍛?, '钀ヤ笟鍛?, 1),
('MANAGER', '缁忕悊', '缁忕悊', 1),
('SORTER', '灏佸彂鎿嶄綔鍛?, '灏佸彂鎿嶄綔鍛?, 1),
('ADMIN', '绯荤粺绠＄悊鍛?, '绯荤粺绠＄悊鍛?, 1);

INSERT INTO `permission` (`code`, `name`, `description`, `status`) VALUES
('MAIL_CREATE', '閭欢鏀跺瘎', '閭欢鏀跺瘎', 1),
('MAIL_QUERY', '閭欢鏌ヨ', '閭欢鏌ヨ', 1),
('TRACK_QUERY', '杞ㄨ抗鏌ヨ', '杞ㄨ抗鏌ヨ', 1),
('TOKEN_ISSUE', 'Token绛惧彂', 'Token绛惧彂', 1),
('USER_ADMIN', '鐢ㄦ埛绠＄悊', '鐢ㄦ埛绠＄悊', 1),
('ROLE_ADMIN', '瑙掕壊绠＄悊', '瑙掕壊绠＄悊', 1),
('DISPATCH_BAG_CREATE', '鎬诲寘鍒涘缓', '鎬诲寘鍒涘缓', 1),
('DISPATCH_BAG_HANDOFF', '鎬诲寘浜ゆ帴', '鎬诲寘浜ゆ帴', 1);

INSERT INTO `user` (`username`, `password_hash`, `display_name`, `facility_code`, `phone`, `email`, `status`) VALUES
('clerk001', '$2a$10$wX6zY2Zqk8YgNq2h4XvY5e0mQ0X4uX3x9W0pK2d1qZx1vY5N9r9aG', 'Namoa Clerk', 'B1', '13800000001', 'clerk001@liana.post', 1),
('manager001', '$2a$10$wX6zY2Zqk8YgNq2h4XvY5e0mQ0X4uX3x9W0pK2d1qZx1vY5N9r9aG', 'Namoa Manager', 'B1', '13800000002', 'manager001@liana.post', 1),
('sorter001', '$2a$10$wX6zY2Zqk8YgNq2h4XvY5e0mQ0X4uX3x9W0pK2d1qZx1vY5N9r9aG', 'Transfer Sorter', 'A1', '13800000003', 'sorter001@liana.post', 1),
('admin001', '$2a$10$wX6zY2Zqk8YgNq2h4XvY5e0mQ0X4uX3x9W0pK2d1qZx1vY5N9r9aG', 'System Admin', 'A1', '13800000004', 'admin001@liana.post', 1);

INSERT INTO `user_role` (`user_id`, `role_id`) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4);

INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
(1, 1), (1, 2), (1, 3), (1, 7), (1, 8),
(2, 1), (2, 2), (2, 3), (2, 7), (2, 8),
(3, 2), (3, 3), (3, 7), (3, 8),
(4, 1), (4, 2), (4, 3), (4, 4), (4, 5), (4, 6), (4, 7), (4, 8);
