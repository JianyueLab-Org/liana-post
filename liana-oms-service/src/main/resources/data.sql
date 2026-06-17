INSERT INTO `mail_type` (`code`, `name`, `description`, `requires_signature`) VALUES
('C', '包裹', '普通包裹', 0),
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

INSERT INTO `sender` (`full_name`, `phone`, `id_type`, `id_number`, `address`, `postcode`, `country_code`) VALUES
('Demo Sender', '0900123456', 'NID', 'NID0001', 'A1 Plaza, Namoa', '10001', 'LN');

INSERT INTO `recipient` (`full_name`, `phone`, `address`, `postcode`, `country_code`) VALUES
('Demo Recipient', '0900654321', 'B1 Central, Namoa', '10002', 'LN');

INSERT INTO `mail` (`waybill_no`, `bag_no`, `package_id`, `mail_type_code`, `service_type`, `mail_scope`, `dest_country_code`, `sender_id`, `recipient_id`, `origin_facility_code`, `current_facility_code`, `current_slot`, `dest_facility_code`, `destination_node`, `status`, `weight_grams`, `declared_value`) VALUES
('EE123456785LN', NULL, NULL, 'R', 'AIR', 'DOMESTIC', NULL, 1, 1, 'B1', 'B1', NULL, 'C1', NULL, 'CREATED', 120, NULL);
