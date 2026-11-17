-- Successful command receipts outlive current mail.package_id, so late retries cannot re-pack opened mail.
CREATE TABLE oms_packing_inbox (
  bag_no VARCHAR(32) NOT NULL,
  event_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_json JSON NOT NULL,
  recorded_at DATETIME(3) NOT NULL,
  applied_at DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (bag_no),
  UNIQUE KEY uk_oms_packing_event (event_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
