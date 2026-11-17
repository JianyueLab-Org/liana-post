INSERT INTO `mail_type` (`code`, `name`, `description`, `requires_signature`) VALUES
('C', '包裹', '普通包裹', 0),
('U', '信函', '可选挂号的信函', 0),
('R', '挂号信', '挂号信邮件', 1),
('E', 'EMS', '特快专递', 1);

INSERT INTO `service_type` (`code`, `name`, `description`, `enabled`) VALUES
('AIR', '航空', '航空运输服务', 1),
('SAL', 'SAL', '空陆联运服务', 1),
('SEA', '海运', '海运运输服务', 1);

INSERT INTO `country` (`code`, `name`, `english_name`, `postal_enabled`, `upu_region`, `remark`) VALUES
('LN', '利亚纳', 'Liana', 1, 'OCEANIA', '默认本国示例'),
('HK', '中国香港', 'Hong Kong', 1, 'ASIA_PACIFIC', 'UPU示例目的国'),
('JP', '日本', 'Japan', 1, 'ASIA_PACIFIC', 'UPU示例目的国'),
('US', '美国', 'United States', 1, 'NORTH_AMERICA', 'UPU示例目的国'),
('DE', '德国', 'Germany', 0, 'EUROPE', '示例：当前未通邮');

INSERT INTO `country_service_type` (`country_code`, `service_type_code`, `enabled`) VALUES
('LN', 'AIR', 1),
('LN', 'SAL', 1),
('LN', 'SEA', 1),
('HK', 'AIR', 1),
('HK', 'SAL', 1),
('HK', 'SEA', 1),
('JP', 'AIR', 1),
('JP', 'SAL', 1),
('JP', 'SEA', 1),
('US', 'AIR', 1),
('US', 'SAL', 1),
('US', 'SEA', 1),
('DE', 'AIR', 0),
('DE', 'SAL', 0),
('DE', 'SEA', 0);
