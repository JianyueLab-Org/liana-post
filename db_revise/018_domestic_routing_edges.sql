-- Add directed handling legs needed by domestic route calculation.
-- Re-runnable and non-destructive; existing route settings remain authoritative.
USE facility_db;

INSERT IGNORE INTO facility_route
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
