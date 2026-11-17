-- Fictional Liana Pacific master data. First import only, transaction managed by tool.

INSERT INTO facility
  (facility_code, name, type_code, parent_facility_code, country_code, province, city, address, latitude, longitude, status)
VALUES
  ('LN-NAM-HQ', '利亚纳国家邮件中心', 'TRANSFER_CENTER', NULL, 'LN', 'Namoa 主岛', 'Namoa', 'National Mail Centre, Namoa', -15.3000000, -151.2000000, 1),
  ('LN-NAM-IMC', '纳莫阿国际邮件互换局', 'INTERNATIONAL_GATEWAY', 'LN-NAM-HQ', 'LN', 'Namoa 主岛', 'Namoa', 'International Mail Centre, Namoa', -15.2800000, -151.1600000, 1),
  ('LN-NAM-AIR', '纳莫阿航空货运站', 'AIR_HUB', 'LN-NAM-HQ', 'LN', 'Namoa 主岛', 'Namoa', 'Air Cargo Terminal, Namoa', -15.3200000, -151.1500000, 1),
  ('LN-NAM-SEA', '纳莫阿主港邮政货棚', 'SEA_HUB', 'LN-NAM-HQ', 'LN', 'Namoa 主岛', 'Namoa', 'Main Port Mail Shed, Namoa', -15.3400000, -151.2400000, 1),
  ('LN-NM-01', '纳莫阿中央邮局', 'POST_OFFICE', 'LN-NAM-HQ', 'LN', 'Namoa 主岛', 'Namoa Central', 'Central Avenue, Namoa 1000', -15.3100000, -151.2100000, 1),
  ('LN-NM-02', '纳莫阿东区邮局', 'POST_OFFICE', 'LN-NAM-HQ', 'LN', 'Namoa 主岛', 'East Ridge', 'East Ridge Road, Namoa 1020', -15.2600000, -151.1700000, 1),
  ('LN-OR-RC', 'Oro 北岛区域邮件中心', 'TRANSFER_CENTER', 'LN-NAM-HQ', 'LN', 'Oro 北岛群', 'Oro Town', 'Regional Mail Centre, Oro 2000', -13.7000000, -148.3000000, 1),
  ('LN-OR-01', 'Oro 镇邮局', 'POST_OFFICE', 'LN-OR-RC', 'LN', 'Oro 北岛群', 'Oro Town', 'Market Street, Oro 2000', -13.7200000, -148.3200000, 1),
  ('LN-OR-02', 'North Reef 邮政代理点', 'POST_OFFICE', 'LN-OR-RC', 'LN', 'Oro 北岛群', 'North Reef', 'Village Hall, North Reef 2090', -13.4500000, -147.9500000, 1),
  ('LN-MI-RC', 'Miri 东岛区域邮件中心', 'TRANSFER_CENTER', 'LN-NAM-HQ', 'LN', 'Miri 东岛群', 'Miri Bay', 'Regional Mail Centre, Miri 3000', -18.6000000, -146.6000000, 1),
  ('LN-MI-01', 'Miri 湾邮局', 'POST_OFFICE', 'LN-MI-RC', 'LN', 'Miri 东岛群', 'Miri Bay', 'Harbour Road, Miri 3000', -18.6300000, -146.6400000, 1),
  ('LN-MI-02', '东岛群邮政代理点', 'POST_OFFICE', 'LN-MI-RC', 'LN', 'Miri 东岛群', 'East Islets', 'Cooperative Store, East Islets 3090', -18.3500000, -146.1500000, 1),
  ('LN-SE-RC', 'Sela 南岛区域邮件中心', 'TRANSFER_CENTER', 'LN-NAM-HQ', 'LN', 'Sela 南岛群', 'Sela Harbour', 'Regional Mail Centre, Sela 4000', -21.1000000, -150.0000000, 1),
  ('LN-SE-01', 'Sela 港邮局', 'POST_OFFICE', 'LN-SE-RC', 'LN', 'Sela 南岛群', 'Sela Harbour', 'Harbour Road, Sela 4000', -21.1300000, -150.0400000, 1),
  ('LN-SE-02', 'Far Reef 邮政代理点', 'POST_OFFICE', 'LN-SE-RC', 'LN', 'Sela 南岛群', 'Far Reef', 'Community Centre, Far Reef 4090', -21.4200000, -149.7500000, 1);

INSERT INTO facility_route
  (route_code, origin_facility_code, destination_facility_code, transport_mode, transport_type, distance_km, estimated_hours, priority_level, status)
VALUES
  ('LN-NAM-CITY', 'LN-NAM-HQ', 'LN-NM-01', 'LAND', 'LAND', 8.00, 0.50, 0, 1),
  ('LN-NAM-AIR', 'LN-NAM-AIR', 'LN-OR-RC', 'AIR', 'AIR', 420.00, 1.20, 0, 1),
  ('LN-NAM-SEA', 'LN-NAM-SEA', 'LN-OR-RC', 'SEA', 'SEA', 510.00, 10.00, 1, 1),
  ('LN-NAM-MIRI', 'LN-NAM-AIR', 'LN-MI-RC', 'AIR', 'AIR', 690.00, 1.80, 0, 1),
  ('LN-NAM-SELA', 'LN-NAM-AIR', 'LN-SE-RC', 'AIR', 'AIR', 880.00, 2.40, 0, 1),
  ('LN-OR-OUTER', 'LN-OR-RC', 'LN-OR-02', 'SEA', 'SEA', 76.00, 3.50, 0, 1),
  ('LN-MI-ISLETS', 'LN-MI-RC', 'LN-MI-02', 'SEA', 'SEA', 118.00, 5.00, 0, 1),
  ('LN-SE-FAR', 'LN-SE-RC', 'LN-SE-02', 'SEA', 'SEA', 205.00, 7.00, 0, 1),
  ('LN-IMC-AIR', 'LN-NAM-IMC', 'LN-NAM-AIR', 'LAND', 'LAND', 12.00, 0.70, 0, 1);

INSERT INTO demo_postal_region
  (region_code, region_name, postcode_prefix, center_facility_code, inhabited_islands, coverage_description)
VALUES
  ('NM', 'Namoa 主岛', '10', 'LN-NAM-HQ', 3, '首都、全国邮件中心、国际互换局、机场与主港'),
  ('OR', 'Oro 北岛群', '20', 'LN-OR-RC', 3, 'Oro 主岛、North Reef 与 Outer Cays'),
  ('MI', 'Miri 东岛群', '30', 'LN-MI-RC', 3, 'Miri 主岛、East Islets 与 Coconut Islet'),
  ('SE', 'Sela 南岛群', '40', 'LN-SE-RC', 3, 'Sela 主岛、Far Reef 与 South Keys');

INSERT INTO demo_postal_island (island_code, region_code, island_name, postcode_range, island_type, map_x, map_y)
VALUES
  ('NM-MAIN', 'NM', 'Namoa', '1000-1099', 'MAIN', 177, 127),
  ('NM-SOUTH-CAY', 'NM', 'South Cay', '1090-1094', 'OUTER_CAY', 263, 216),
  ('NM-WEST-CAY', 'NM', 'West Cay', '1095-1099', 'OUTER_CAY', 282, 243),
  ('OR-MAIN', 'OR', 'Oro', '2000-2079', 'MAIN', 573, 57),
  ('OR-NORTH-REEF', 'OR', 'North Reef', '2010-2019,2090-2094', 'REEF', 632, 105),
  ('OR-OUTER-CAYS', 'OR', 'Outer Cays', '2095-2099', 'OUTER_CAY', 659, 150),
  ('MI-MAIN', 'MI', 'Miri', '3000-3079', 'MAIN', 758, 246),
  ('MI-EAST-ISLETS', 'MI', 'East Islets', '3010-3019,3090-3094', 'ISLETS', 817, 338),
  ('MI-COCONUT-ISLET', 'MI', 'Coconut Islet', '3095-3099', 'ISLET', 838, 365),
  ('SE-MAIN', 'SE', 'Sela', '4000-4079', 'MAIN', 515, 376),
  ('SE-FAR-REEF', 'SE', 'Far Reef', '4010-4019,4090-4094', 'REEF', 557, 485),
  ('SE-SOUTH-KEYS', 'SE', 'South Keys', '4095-4099', 'ISLETS', 578, 510);

INSERT INTO demo_facility_profile
  (facility_code, island_code, postcode_coverage, service_coverage, capacity_description, opening_hours, map_x, map_y)
VALUES
  ('LN-NAM-HQ', 'NM-MAIN', '1000-4099', '全国跨岛干线、全境邮包汇集', '每日 18,000 件邮件', '每日 05:00-23:00', 176, 235),
  ('LN-NAM-IMC', 'NM-MAIN', '国际进出口邮件', '全境国际邮件与海关交接', '每日 4,500 件国际邮件', '每日按航班窗口运营', 222, 182),
  ('LN-NAM-AIR', 'NM-MAIN', '国内及国际航空邮件', '机场航空邮件交接', '每日 6 个邮件交接批次', '随航空班次运行', 214, 258),
  ('LN-NAM-SEA', 'NM-MAIN', '国内岛际海运邮件', 'Namoa 至 Oro/Miri/Sela 支线船', '每日 1,200 袋件', '按港口船期运行', 134, 275),
  ('LN-NM-01', 'NM-MAIN', '1000-1009,1080-1089', '首都中心城区与 PO Box', '柜台 6 席；投递 18 班', '周一至五 08:30-16:30', 166, 218),
  ('LN-NM-02', 'NM-MAIN', '1020-1049', '东岭社区、机场与东岸城区', '柜台 3 席；投递 8 班', '周一至五 08:30-16:30', 205, 224),
  ('LN-OR-RC', 'OR-MAIN', '2000-2099', 'Oro 主岛、North Reef 与外礁岛', '每日 3,600 件邮件', '每日 06:00-20:00', 566, 131),
  ('LN-OR-01', 'OR-MAIN', '2000-2009,2080-2089', 'Oro Town 城镇与政府 PO Box', '柜台 2 席；投递 5 班', '周一至五 08:30-16:00', 548, 147),
  ('LN-OR-02', 'OR-NORTH-REEF', '2010-2019,2090-2099', '北礁岛与 Outer Cays 偏远社区', '每日约 240 件邮件', '周一/三/五 09:00-15:00', 628, 126),
  ('LN-MI-RC', 'MI-MAIN', '3000-3099', 'Miri 主岛及东侧小岛', '每日 2,800 件邮件', '每日 06:00-19:00', 758, 323),
  ('LN-MI-01', 'MI-MAIN', '3000-3009,3080-3089', 'Miri Bay 城镇、港口与 PO Box', '柜台 2 席；投递 4 班', '周一至五 08:30-16:00', 735, 305),
  ('LN-MI-02', 'MI-EAST-ISLETS', '3010-3019,3090-3099', '椰林平原与东侧岛屿', '每日约 180 件邮件', '周二/四/六 09:00-14:00', 812, 355),
  ('LN-SE-RC', 'SE-MAIN', '4000-4099', 'Sela 主岛南岸与外海岛礁', '每日 2,100 件邮件', '每日 06:00-18:00', 517, 444),
  ('LN-SE-01', 'SE-MAIN', '4000-4009,4080-4089', '港区、旅游区及政府 PO Box', '柜台 2 席；投递 3 班', '周一至五 08:30-16:00', 496, 427),
  ('LN-SE-02', 'SE-FAR-REEF', '4010-4019,4090-4099', '远礁岛与南部季节性社区', '每周约 320 件邮件', '周二/五 10:00-14:00', 556, 493);

INSERT INTO demo_route_profile (route_code, frequency_description, operating_note)
VALUES
  ('LN-NAM-CITY', '每日多班；05:30-22:00', '城市集散与首都邮局交接'),
  ('LN-NAM-AIR', '每日 1 班；周六加班', '国内航空主干线；停航时转入支线船计划'),
  ('LN-NAM-SEA', '每周 5 班；周日减班', '航空取消时的备用运输方式'),
  ('LN-NAM-MIRI', '每日 1 班', 'Miri 岛群日常航空交接'),
  ('LN-NAM-SELA', '每日 1 班；风暴季视天气', '风暴季可能暂停；邮件留存安全网点等待恢复'),
  ('LN-OR-OUTER', '每周 4 班；潮汐窗口', '偏远代理点可离线扫描，回网后补传事件'),
  ('LN-MI-ISLETS', '每周 3 班', '邮件需等待合适潮汐与返航窗口'),
  ('LN-SE-FAR', '每周 2 班；风暴季停航', '全年天气敏感度最高的邮路'),
  ('LN-IMC-AIR', '每日按截关批次', '国际邮件安检、封发后交航司');

INSERT INTO facility_route
  (route_code, origin_facility_code, destination_facility_code, transport_mode, transport_type, distance_km, estimated_hours, priority_level, status)
VALUES
  ('LN-NM-01-HQ', 'LN-NM-01', 'LN-NAM-HQ', 'LAND', 'LAND', 8.00, 0.50, 0, 1),
  ('LN-NM-02-HQ', 'LN-NM-02', 'LN-NAM-HQ', 'LAND', 'LAND', 12.00, 0.75, 0, 1),
  ('LN-HQ-AIR', 'LN-NAM-HQ', 'LN-NAM-AIR', 'LAND', 'LAND', 12.00, 0.70, 0, 1),
  ('LN-OR-01-RC', 'LN-OR-01', 'LN-OR-RC', 'LAND', 'LAND', 6.00, 0.50, 0, 1),
  ('LN-OR-02-RC', 'LN-OR-02', 'LN-OR-RC', 'SEA', 'SEA', 76.00, 3.50, 0, 1),
  ('LN-MI-01-RC', 'LN-MI-01', 'LN-MI-RC', 'LAND', 'LAND', 5.00, 0.50, 0, 1),
  ('LN-MI-02-RC', 'LN-MI-02', 'LN-MI-RC', 'SEA', 'SEA', 118.00, 5.00, 0, 1),
  ('LN-SE-01-RC', 'LN-SE-01', 'LN-SE-RC', 'LAND', 'LAND', 5.00, 0.50, 0, 1),
  ('LN-SE-02-RC', 'LN-SE-02', 'LN-SE-RC', 'SEA', 'SEA', 205.00, 7.00, 0, 1),
  ('LN-OR-RC-AIR', 'LN-OR-RC', 'LN-NAM-AIR', 'AIR', 'AIR', 420.00, 1.20, 0, 1),
  ('LN-MI-RC-AIR', 'LN-MI-RC', 'LN-NAM-AIR', 'AIR', 'AIR', 690.00, 1.80, 0, 1),
  ('LN-SE-RC-AIR', 'LN-SE-RC', 'LN-NAM-AIR', 'AIR', 'AIR', 880.00, 2.40, 0, 1),
  ('LN-AIR-HQ', 'LN-NAM-AIR', 'LN-NAM-HQ', 'LAND', 'LAND', 12.00, 0.70, 0, 1);
