-- Actual bag receipt is separate from expected prealert and from opening its mail contents.
CREATE TABLE sorting_receive_request (
  request_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  manifest_no VARCHAR(64) NOT NULL,
  version_no INT NOT NULL,
  content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_json JSON NOT NULL,
  receipt_json JSON NOT NULL,
  received_at DATETIME(3) NOT NULL,
  CONSTRAINT fk_sorting_receive_version FOREIGN KEY (manifest_no, version_no) REFERENCES sorting_manifest_version(manifest_no, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sorting_bag_receipt (
  event_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  request_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  manifest_no VARCHAR(64) NOT NULL,
  version_no INT NOT NULL,
  bag_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  assignment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  task_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  source_org_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  destination_org_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  actor_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  received_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_sorting_bag_receipt (manifest_no, bag_no),
  KEY idx_sorting_receipt_bag (bag_no, received_at, event_no),
  CONSTRAINT fk_sorting_receipt_request FOREIGN KEY (request_no) REFERENCES sorting_receive_request(request_no),
  CONSTRAINT fk_sorting_receipt_version FOREIGN KEY (manifest_no, version_no) REFERENCES sorting_manifest_version(manifest_no, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sorting_bag_position (
  bag_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  facility_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  last_manifest_no VARCHAR(64) NOT NULL,
  last_event_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  container_status VARCHAR(16) NOT NULL DEFAULT 'SEALED',
  updated_at DATETIME(3) NOT NULL,
  lock_version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_sorting_position_receipt FOREIGN KEY (last_event_no) REFERENCES sorting_bag_receipt(event_no),
  CONSTRAINT ck_sorting_container_status CHECK (container_status IN ('SEALED','OPENED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sorting_receipt_outbox (
  event_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  destination VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL,
  last_error VARCHAR(512) DEFAULT NULL,
  receipt_json JSON DEFAULT NULL,
  created_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (event_no, destination),
  KEY idx_sorting_receipt_retry (status, next_attempt_at),
  CONSTRAINT fk_sorting_receipt_outbox FOREIGN KEY (event_no) REFERENCES sorting_bag_receipt(event_no),
  CONSTRAINT ck_sorting_receipt_destination CHECK (destination IN ('TRANSPORT','DISPATCH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
