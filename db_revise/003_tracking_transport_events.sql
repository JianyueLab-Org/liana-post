USE `tracking_db`;

INSERT INTO `tracking_event` (`event_no`, `waybill_no`, `event_type`, `event_time`, `facility_code`, `facility_name`, `operator_id`, `operator_name`, `payload`, `source_service`, `created_at`)
VALUES
('T202606110001', 'LYN0000000001LN', 'TRANSPORT_ASSIGNED', '2026-06-11 08:00:00', 'B1', 'Demo Facility', NULL, NULL, '{"taskCode":"TT-20260611-001"}', 'TRANSPORT', NOW());
