-- Durable receipt prevents a late opening retry from detaching mail from a newly packed bag.
CREATE TABLE oms_unpacking_inbox (
  event_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  bag_no VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  request_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
  request_json JSON NOT NULL,
  receipt_json JSON DEFAULT NULL,
  occurred_at DATETIME(3) NOT NULL,
  recorded_at DATETIME(3) NOT NULL,
  applied_at DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (event_no),
  UNIQUE KEY uk_oms_unpacking_bag (bag_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
