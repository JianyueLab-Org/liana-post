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

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p
ON r.code = 'ADMIN'
 OR (r.code IN ('CLERK','MANAGER') AND p.code IN ('MAIL_CREATE','MAIL_QUERY','TRACK_QUERY','DISPATCH_BAG_CREATE','DISPATCH_BAG_HANDOFF'))
 OR (r.code = 'SORTER' AND p.code IN ('MAIL_QUERY','TRACK_QUERY','DISPATCH_BAG_CREATE','DISPATCH_BAG_HANDOFF'));
