-- Current custody is fenced by the bag row; every outgoing leg retains its own history.
ALTER TABLE sorting_bag_position ADD COLUMN outgoing_manifest_no VARCHAR(64) DEFAULT NULL;

CREATE TABLE sorting_forward_request (
  request_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  manifest_no VARCHAR(64) NOT NULL,
  previous_manifest_no VARCHAR(64) NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL,
  last_error VARCHAR(512) DEFAULT NULL,
  receipt_json JSON DEFAULT NULL,
  created_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) DEFAULT NULL,
  UNIQUE KEY uk_sorting_forward_manifest (manifest_no),
  KEY idx_sorting_forward_retry (status, next_attempt_at),
  CONSTRAINT fk_sorting_forward_previous FOREIGN KEY (previous_manifest_no) REFERENCES sorting_manifest(manifest_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sorting_bag_handoff (
  manifest_no VARCHAR(64) NOT NULL,
  bag_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  request_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  previous_event_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_org_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  destination_org_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
  created_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (manifest_no, bag_no),
  KEY idx_sorting_handoff_bag (bag_no, created_at),
  CONSTRAINT fk_sorting_handoff_request FOREIGN KEY (request_no) REFERENCES sorting_forward_request(request_no),
  CONSTRAINT fk_sorting_handoff_receipt FOREIGN KEY (previous_event_no) REFERENCES sorting_bag_receipt(event_no),
  CONSTRAINT ck_sorting_handoff_status CHECK (status IN ('RESERVED','RECEIVED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
