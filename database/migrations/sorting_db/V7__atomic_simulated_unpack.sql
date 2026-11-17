-- One completed opening per physical bag. Request/response and delivery command are immutable.
CREATE TABLE sorting_unpack_request (
  request_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  event_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  manifest_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  bag_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  facility_code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  actor_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  request_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
  request_json JSON NOT NULL,
  response_json JSON NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL,
  last_error VARCHAR(512) DEFAULT NULL,
  receipt_json JSON DEFAULT NULL,
  occurred_at DATETIME(3) NOT NULL,
  recorded_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (request_no),
  UNIQUE KEY uk_sorting_unpack_event (event_no),
  UNIQUE KEY uk_sorting_unpack_bag (bag_no),
  KEY idx_sorting_unpack_due (status,next_attempt_at),
  KEY idx_sorting_unpack_manifest (manifest_no,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sorting_unpack_item (
  event_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  mail_no VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  classification VARCHAR(16) NOT NULL,
  PRIMARY KEY (event_no,mail_no),
  KEY idx_sorting_unpack_mail (mail_no,event_no),
  CONSTRAINT fk_sorting_unpack_item_event FOREIGN KEY (event_no) REFERENCES sorting_unpack_request(event_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE package_item_line MODIFY COLUMN operator_id VARCHAR(64) DEFAULT NULL;
