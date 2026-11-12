USE `transport_db`;

ALTER TABLE `transport_route`
  MODIFY COLUMN `origin_facility_id` VARCHAR(64) NOT NULL,
  MODIFY COLUMN `destination_facility_id` VARCHAR(64) NOT NULL;

UPDATE `transport_route`
SET `origin_facility_id` = 'A1',
    `destination_facility_id` = 'B1'
WHERE `route_code` IN ('TR-A1-B1-SEA', 'TR-A1-B1-AIR');

UPDATE `transport_route`
SET `origin_facility_id` = 'B1',
    `destination_facility_id` = 'C1'
WHERE `route_code` = 'TR-B1-C1-LAND';
