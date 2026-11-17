CREATE TABLE sorting_repack_request (
  request_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  bag_no VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  facility_code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  request_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
  request_json JSON NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL,
  last_error VARCHAR(512) DEFAULT NULL,
  receipt_json JSON DEFAULT NULL,
  occurred_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (request_no),
  UNIQUE KEY uk_sorting_repack_bag (bag_no),
  KEY idx_sorting_repack_due (status,next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sorting_repack_item (
  unpack_event_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  mail_no VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  request_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  source_bag_no VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (unpack_event_no,mail_no),
  KEY idx_sorting_repack_request (request_no),
  CONSTRAINT fk_sorting_repack_request FOREIGN KEY (request_no) REFERENCES sorting_repack_request(request_no),
  CONSTRAINT fk_sorting_repack_scan FOREIGN KEY (unpack_event_no,mail_no) REFERENCES sorting_unpack_item(event_no,mail_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
