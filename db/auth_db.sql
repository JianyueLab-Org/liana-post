INSERT INTO `role` (`code`, `name`, `description`, `status`) VALUES
('CLERK', '营业员', '营业员', 1),
('MANAGER', '经理', '经理', 1),
('SORTER', '封发操作员', '封发操作员', 1),
('ADMIN', '系统管理员', '系统管理员', 1);

INSERT INTO `permission` (`code`, `name`, `description`, `status`) VALUES
('MAIL_CREATE', '邮件收寄', '邮件收寄', 1),
('MAIL_QUERY', '邮件查询', '邮件查询', 1),
('TRACK_QUERY', '轨迹查询', '轨迹查询', 1),
('TOKEN_ISSUE', 'Token签发', 'Token签发', 1),
('USER_ADMIN', '用户管理', '用户管理', 1),
('ROLE_ADMIN', '角色管理', '角色管理', 1),
('DISPATCH_BAG_CREATE', '总包创建', '总包创建', 1),
('DISPATCH_BAG_HANOFF', '总包交接', '总包交接', 1);
