USE `facility_db`;

ALTER TABLE `facility_route`
  ADD COLUMN `transport_type` VARCHAR(32) DEFAULT NULL COMMENT '运输类型 SEA/AIR/LAND' AFTER `transport_mode`;

UPDATE `facility_route`
SET `transport_type` = CASE
  WHEN `transport_mode` = 'TRUCK' THEN 'LAND'
  WHEN `transport_mode` = 'AIR' THEN 'AIR'
  WHEN `transport_mode` = 'SEA' THEN 'SEA'
  ELSE `transport_mode`
END;

ALTER TABLE `facility_route`
  ADD INDEX `idx_facility_route_transport_type` (`transport_type`);
