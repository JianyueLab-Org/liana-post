USE auth_db;

UPDATE `role`
SET `name` = '邮局营业员',
    `description` = '负责邮件收寄、邮件查询和基础投递作业'
WHERE `code` = 'CLERK';

UPDATE `role`
SET `name` = '邮局经理',
    `description` = '负责网点业务管理、邮件查询和运营查看'
WHERE `code` = 'MANAGER';

UPDATE `role`
SET `name` = '转运中心操作员',
    `description` = '负责分拣、封发、交接和运输协同作业'
WHERE `code` = 'SORTER';

UPDATE `role`
SET `name` = '系统管理员',
    `description` = '负责用户、角色、权限和系统基础数据维护'
WHERE `code` = 'ADMIN';

UPDATE `permission`
SET `name` = '邮件收寄',
    `description` = '创建邮件并生成邮件台账'
WHERE `code` = 'MAIL_CREATE';

UPDATE `permission`
SET `name` = '邮件查询',
    `description` = '查询邮件台账、详情和投递数据'
WHERE `code` = 'MAIL_QUERY';

UPDATE `permission`
SET `name` = '轨迹查询',
    `description` = '查询邮件全生命周期轨迹'
WHERE `code` = 'TRACK_QUERY';

UPDATE `permission`
SET `name` = 'Token签发',
    `description` = '签发和校验登录令牌'
WHERE `code` = 'TOKEN_ISSUE';

UPDATE `permission`
SET `name` = '用户管理',
    `description` = '维护用户账号和所属角色'
WHERE `code` = 'USER_ADMIN';

UPDATE `permission`
SET `name` = '角色管理',
    `description` = '查看角色和权限配置'
WHERE `code` = 'ROLE_ADMIN';

UPDATE `permission`
SET `name` = '总包创建',
    `description` = '创建总包和封发批次'
WHERE `code` = 'DISPATCH_BAG_CREATE';

UPDATE `permission`
SET `name` = '总包交接',
    `description` = '处理总包交接确认'
WHERE `code` = 'DISPATCH_BAG_HANDOFF';
