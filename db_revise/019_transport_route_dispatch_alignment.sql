-- Align dispatch rule, facility edge and transport service without changing historical rule codes.
USE transport_db;
SET @has_edge := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'transport_route' AND column_name = 'facility_route_code');
SET @ddl := IF(@has_edge = 0, 'ALTER TABLE transport_route ADD COLUMN facility_route_code VARCHAR(64) NULL AFTER route_code', 'SELECT 1');
PREPARE statement FROM @ddl; EXECUTE statement; DEALLOCATE PREPARE statement;
UPDATE transport_route tr JOIN facility_db.facility_route edge ON edge.route_code = tr.route_code
  AND edge.origin_facility_code = tr.origin_facility_id
  AND edge.destination_facility_code = tr.destination_facility_id
  AND edge.transport_mode = tr.transport_type
SET tr.facility_route_code = edge.route_code WHERE tr.facility_route_code IS NULL;
SET @has_edge_index := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'transport_route' AND index_name = 'idx_transport_route_facility_edge');
SET @ddl := IF(@has_edge_index = 0, 'ALTER TABLE transport_route ADD INDEX idx_transport_route_facility_edge (facility_route_code)', 'SELECT 1');
PREPARE statement FROM @ddl; EXECUTE statement; DEALLOCATE PREPARE statement;
CREATE TABLE IF NOT EXISTS transport_task_bag (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  dispatch_bag_id BIGINT NOT NULL,
  bag_no VARCHAR(32) NOT NULL,
  weight_grams INT NOT NULL DEFAULT 0,
  linked_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_transport_task_bag_dispatch (dispatch_bag_id),
  KEY idx_transport_task_bag_task (task_id),
  CONSTRAINT fk_transport_task_bag_task FOREIGN KEY (task_id) REFERENCES transport_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET @has_linked_at := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'transport_task_bag' AND column_name = 'linked_at');
SET @ddl := IF(@has_linked_at = 0, 'ALTER TABLE transport_task_bag ADD COLUMN linked_at DATETIME NULL AFTER weight_grams', 'SELECT 1');
PREPARE statement FROM @ddl; EXECUTE statement; DEALLOCATE PREPARE statement;
INSERT IGNORE INTO transport_task_bag (task_id, dispatch_bag_id, bag_no, weight_grams)
SELECT task.id, task.dispatch_bag_id, bag.bag_no, COALESCE(bag.total_weight_grams, 0)
FROM transport_task task JOIN dispatch_db.dispatch_bag bag ON bag.id = task.dispatch_bag_id
WHERE task.dispatch_bag_id IS NOT NULL ORDER BY task.created_at, task.id;

USE dispatch_db;
SET @has_bag_edge := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dispatch_bag' AND column_name = 'facility_route_code');
SET @ddl := IF(@has_bag_edge = 0, 'ALTER TABLE dispatch_bag ADD COLUMN facility_route_code VARCHAR(64) NULL AFTER route_code', 'SELECT 1');
PREPARE statement FROM @ddl; EXECUTE statement; DEALLOCATE PREPARE statement;
-- Backfill only rows with an unambiguous active edge matching the saved rule mode.
UPDATE dispatch_bag bag JOIN route_rule rule ON rule.rule_code = bag.route_code
JOIN facility_db.facility_route edge ON edge.origin_facility_code = bag.origin_facility_code
 AND edge.destination_facility_code = bag.destination_facility_code
 AND edge.transport_mode = CASE WHEN rule.transport_mode = 'TRUCK' THEN 'LAND' ELSE rule.transport_mode END
 AND edge.status = 1
SET bag.facility_route_code = edge.route_code
WHERE bag.facility_route_code IS NULL AND
 (SELECT COUNT(*) FROM facility_db.facility_route candidate
  WHERE candidate.origin_facility_code = bag.origin_facility_code
    AND candidate.destination_facility_code = bag.destination_facility_code
    AND candidate.transport_mode = edge.transport_mode AND candidate.status = 1) = 1;
SET @has_bag_index := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dispatch_bag' AND index_name = 'idx_dispatch_bag_facility_edge');
SET @ddl := IF(@has_bag_index = 0, 'ALTER TABLE dispatch_bag ADD INDEX idx_dispatch_bag_facility_edge (facility_route_code)', 'SELECT 1');
PREPARE statement FROM @ddl; EXECUTE statement; DEALLOCATE PREPARE statement;

INSERT INTO route_rule (rule_code, source_facility_code, target_facility_code, route_scope, dest_country_code, export_facility_code, priority_level, transport_mode, enabled) VALUES
 ('LN-OR-01-RC-RULE', 'LN-OR-01', 'LN-OR-RC', 'DOMESTIC', NULL, NULL, 0, 'LAND', 1),
 ('LN-OR-RC-NAM-AIR-RULE', 'LN-OR-RC', 'LN-NAM-AIR', 'DOMESTIC', NULL, NULL, 0, 'AIR', 1),
 ('LN-NAM-AIR-HQ-RULE', 'LN-NAM-AIR', 'LN-NAM-HQ', 'DOMESTIC', NULL, NULL, 0, 'LAND', 1)
ON DUPLICATE KEY UPDATE source_facility_code = VALUES(source_facility_code), target_facility_code = VALUES(target_facility_code), transport_mode = VALUES(transport_mode), enabled = VALUES(enabled);

USE transport_db;
INSERT INTO transport_asset (code, name, type, capacity, status) VALUES
 ('LN-AIR-OR-01', 'Oro Return Airlink', 'AIRCRAFT', 1800.00, 'AVAILABLE'),
 ('LN-LAND-OR-COLLECT', 'Oro Collection Truck', 'TRUCK', 80.00, 'AVAILABLE'),
 ('LN-LAND-OR-01', 'Namoa Air Cargo Truck', 'TRUCK', 80.00, 'AVAILABLE')
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type), capacity = VALUES(capacity), status = VALUES(status);
INSERT INTO transport_route (route_code, facility_route_code, origin_facility_id, destination_facility_id, transport_type, estimated_hours, status) VALUES
 ('LN-OR-01-RC', 'LN-OR-01-RC', 'LN-OR-01', 'LN-OR-RC', 'LAND', 0.50, 'ACTIVE'),
 ('LN-OR-RC-AIR', 'LN-OR-RC-AIR', 'LN-OR-RC', 'LN-NAM-AIR', 'AIR', 1.20, 'ACTIVE'),
 ('LN-AIR-HQ', 'LN-AIR-HQ', 'LN-NAM-AIR', 'LN-NAM-HQ', 'LAND', 0.70, 'ACTIVE')
ON DUPLICATE KEY UPDATE facility_route_code = VALUES(facility_route_code), origin_facility_id = VALUES(origin_facility_id), destination_facility_id = VALUES(destination_facility_id), transport_type = VALUES(transport_type), estimated_hours = VALUES(estimated_hours), status = VALUES(status);
-- Date-specific instances for the next seven days; old historical schedules are retained.
INSERT INTO transport_schedule (schedule_code, asset_id, route_id, departure_time, arrival_time, weekday, status)
SELECT CONCAT('DEMO-OR-COLLECT-', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY), '%Y%m%d')), asset.id, route.id,
 DATE_ADD(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY), INTERVAL 6 HOUR),
 DATE_ADD(DATE_ADD(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY), INTERVAL 6 HOUR), INTERVAL 30 MINUTE),
 UPPER(LEFT(DAYNAME(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY)), 3)), 'ACTIVE'
FROM (SELECT 1 day_offset UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) days
JOIN transport_asset asset ON asset.code = 'LN-LAND-OR-COLLECT'
JOIN transport_route route ON route.route_code = 'LN-OR-01-RC'
ON DUPLICATE KEY UPDATE route_id = VALUES(route_id), asset_id = VALUES(asset_id);
INSERT INTO transport_schedule (schedule_code, asset_id, route_id, departure_time, arrival_time, weekday, status)
SELECT CONCAT('DEMO-OR-NAM-', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY), '%Y%m%d')), asset.id, route.id,
 DATE_ADD(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY), INTERVAL 8 HOUR),
 DATE_ADD(DATE_ADD(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY), INTERVAL 8 HOUR), INTERVAL 72 MINUTE),
 UPPER(LEFT(DAYNAME(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY)), 3)), 'ACTIVE'
FROM (SELECT 1 day_offset UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) days
JOIN transport_asset asset ON asset.code = 'LN-AIR-OR-01'
JOIN transport_route route ON route.route_code = 'LN-OR-RC-AIR'
ON DUPLICATE KEY UPDATE route_id = VALUES(route_id), asset_id = VALUES(asset_id);
INSERT INTO transport_schedule (schedule_code, asset_id, route_id, departure_time, arrival_time, weekday, status)
SELECT CONCAT('DEMO-NAM-AIR-HQ-', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY), '%Y%m%d')), asset.id, route.id,
 DATE_ADD(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY), INTERVAL 10 HOUR),
 DATE_ADD(DATE_ADD(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY), INTERVAL 10 HOUR), INTERVAL 42 MINUTE),
 UPPER(LEFT(DAYNAME(DATE_ADD(CURDATE(), INTERVAL days.day_offset DAY)), 3)), 'ACTIVE'
FROM (SELECT 1 day_offset UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) days
JOIN transport_asset asset ON asset.code = 'LN-LAND-OR-01'
JOIN transport_route route ON route.route_code = 'LN-AIR-HQ'
ON DUPLICATE KEY UPDATE route_id = VALUES(route_id), asset_id = VALUES(asset_id);
