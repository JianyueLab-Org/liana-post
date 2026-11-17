CREATE TABLE dispatch_manifest_close_inbox (
  manifest_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  request_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  payload_json JSON NOT NULL,
  receipt_json JSON NOT NULL,
  confirmed_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_dispatch_close_request (request_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
