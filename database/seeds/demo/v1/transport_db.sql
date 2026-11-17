INSERT INTO transport_asset (code, name, type, capacity, status)
VALUES
  ('LN-AIR-01', 'Liana Airlink 01', 'AIRCRAFT', 1800.00, 'AVAILABLE'),
  ('LN-SEA-01', 'Liana Islander 01', 'SHIP', 1200.00, 'AVAILABLE'),
  ('LN-LAND-01', 'Namoa Postal Truck 01', 'TRUCK', 80.00, 'AVAILABLE');

INSERT INTO transport_route
  (route_code, facility_route_code, origin_facility_id, destination_facility_id, transport_type, estimated_hours, status)
VALUES
  ('LN-NAM-CITY', 'LN-NAM-CITY', 'LN-NAM-HQ', 'LN-NM-01', 'LAND', 0.50, 'ACTIVE'),
  ('LN-NAM-AIR', 'LN-NAM-AIR', 'LN-NAM-AIR', 'LN-OR-RC', 'AIR', 1.20, 'ACTIVE'),
  ('LN-NAM-SEA', 'LN-NAM-SEA', 'LN-NAM-SEA', 'LN-OR-RC', 'SEA', 10.00, 'ACTIVE'),
  ('LN-NAM-MIRI', 'LN-NAM-MIRI', 'LN-NAM-AIR', 'LN-MI-RC', 'AIR', 1.80, 'ACTIVE'),
  ('LN-NAM-SELA', 'LN-NAM-SELA', 'LN-NAM-AIR', 'LN-SE-RC', 'AIR', 2.40, 'ACTIVE'),
  ('LN-OR-OUTER', 'LN-OR-OUTER', 'LN-OR-RC', 'LN-OR-02', 'SEA', 3.50, 'ACTIVE'),
  ('LN-MI-ISLETS', 'LN-MI-ISLETS', 'LN-MI-RC', 'LN-MI-02', 'SEA', 5.00, 'ACTIVE'),
  ('LN-SE-FAR', 'LN-SE-FAR', 'LN-SE-RC', 'LN-SE-02', 'SEA', 7.00, 'ACTIVE'),
  ('LN-IMC-AIR', 'LN-IMC-AIR', 'LN-NAM-IMC', 'LN-NAM-AIR', 'LAND', 0.70, 'ACTIVE');

INSERT INTO transport_asset (code, name, type, capacity, status) VALUES
 ('LN-AIR-OR-01', 'Oro Return Airlink', 'AIRCRAFT', 1800.00, 'AVAILABLE'),
 ('LN-LAND-OR-COLLECT', 'Oro Collection Truck', 'TRUCK', 80.00, 'AVAILABLE'),
 ('LN-LAND-OR-01', 'Namoa Air Cargo Truck', 'TRUCK', 80.00, 'AVAILABLE');

INSERT INTO transport_route (route_code, facility_route_code, origin_facility_id, destination_facility_id, transport_type, estimated_hours, status) VALUES
 ('LN-OR-01-RC', 'LN-OR-01-RC', 'LN-OR-01', 'LN-OR-RC', 'LAND', 0.50, 'ACTIVE'),
 ('LN-OR-RC-AIR', 'LN-OR-RC-AIR', 'LN-OR-RC', 'LN-NAM-AIR', 'AIR', 1.20, 'ACTIVE'),
 ('LN-AIR-HQ', 'LN-AIR-HQ', 'LN-NAM-AIR', 'LN-NAM-HQ', 'LAND', 0.70, 'ACTIVE');

INSERT INTO transport_asset (code,name,type,capacity,status) VALUES
('LN-AIR-MIRI','Miri Airlink','AIRCRAFT',1800.00,'AVAILABLE'),
('LN-AIR-SELA','Sela Airlink','AIRCRAFT',1800.00,'AVAILABLE'),
('LN-SEA-ORO','North Reef Postal Boat','SHIP',200.00,'AVAILABLE'),
('LN-SEA-MIRI','East Islets Postal Boat','SHIP',200.00,'AVAILABLE'),
('LN-SEA-SELA','Far Reef Postal Boat','SHIP',200.00,'AVAILABLE'),
('LN-LAND-IMC','International Cargo Truck','TRUCK',80.00,'AVAILABLE');

SET @liana_business_day = DATE(UTC_TIMESTAMP() + INTERVAL 12 HOUR) + INTERVAL 1 DAY;

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-NAM-CITY-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,330,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,360,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-NAM-CITY' WHERE a.code='LN-LAND-01';

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-NAM-AIR-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,420,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,492,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-NAM-AIR' WHERE a.code='LN-AIR-01';

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-NAM-SEA-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,480,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,1080,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-NAM-SEA' WHERE a.code='LN-SEA-01';

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-NAM-MIRI-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,540,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,648,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-NAM-MIRI' WHERE a.code='LN-AIR-MIRI';

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-NAM-SELA-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,660,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,804,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-NAM-SELA' WHERE a.code='LN-AIR-SELA';

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-OR-OUTER-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,540,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,750,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-OR-OUTER' WHERE a.code='LN-SEA-ORO';

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-MI-ISLETS-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,420,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,720,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-MI-ISLETS' WHERE a.code='LN-SEA-MIRI';

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-SE-FAR-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,360,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,780,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-SE-FAR' WHERE a.code='LN-SEA-SELA';

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-IMC-AIR-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,840,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,882,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-IMC-AIR' WHERE a.code='LN-LAND-IMC';

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-OR-01-RC-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,360,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,390,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-OR-01-RC' WHERE a.code='LN-LAND-OR-COLLECT';

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-OR-RC-AIR-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,480,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,552,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-OR-RC-AIR' WHERE a.code='LN-AIR-OR-01';

INSERT INTO transport_schedule (schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status)
SELECT CONCAT('DEMO-LN-AIR-HQ-',DATE_FORMAT(@liana_business_day,'%Y%m%d')), a.id,r.id,
 TIMESTAMPADD(MINUTE,600,@liana_business_day) - INTERVAL 12 HOUR,
 TIMESTAMPADD(MINUTE,642,@liana_business_day) - INTERVAL 12 HOUR,
 UPPER(LEFT(DAYNAME(@liana_business_day),3)), 'ACTIVE'
FROM transport_asset a JOIN transport_route r ON r.route_code='LN-AIR-HQ' WHERE a.code='LN-LAND-OR-01';
