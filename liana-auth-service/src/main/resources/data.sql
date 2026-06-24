INSERT INTO `role` (`code`, `name`, `description`, `status`) VALUES
('CLERK', '邮局营业员', '负责邮件收寄、邮件查询和基础投递作业', 1),
('MANAGER', '邮局经理', '负责网点业务管理、邮件查询和运营查看', 1),
('SORTER', '转运中心操作员', '负责分拣、封发、交接和运输协同作业', 1),
('ADMIN', '系统管理员', '负责用户、角色、权限和系统基础数据维护', 1);

INSERT INTO `permission` (`code`, `name`, `description`, `status`) VALUES
('MAIL_CREATE', '邮件收寄', '创建邮件并生成邮件台账', 1),
('MAIL_QUERY', '邮件查询', '查询邮件台账、详情和投递数据', 1),
('TRACK_QUERY', '轨迹查询', '查询邮件全生命周期轨迹', 1),
('TOKEN_ISSUE', 'Token签发', '签发和校验登录令牌', 1),
('USER_ADMIN', '用户管理', '维护用户账号和所属角色', 1),
('ROLE_ADMIN', '角色管理', '查看角色和权限配置', 1),
('DISPATCH_BAG_CREATE', '总包创建', '创建总包和封发批次', 1),
('DISPATCH_BAG_HANDOFF', '总包交接', '处理总包交接确认', 1);

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
