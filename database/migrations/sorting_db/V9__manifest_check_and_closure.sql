-- Check completion freezes the received/missing partition without inventing receipts.
CREATE TABLE sorting_manifest_check (
  manifest_no VARCHAR(64) NOT NULL PRIMARY KEY,
  request_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  request_json JSON NOT NULL,
  arrival_json JSON NOT NULL,
  receipt_json JSON NOT NULL,
  completed_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_sorting_check_request (request_no),
  CONSTRAINT fk_sorting_check_manifest FOREIGN KEY (manifest_no) REFERENCES sorting_manifest(manifest_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sorting_manifest_close (
  manifest_no VARCHAR(64) NOT NULL PRIMARY KEY,
  request_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  request_json JSON NOT NULL,
  payload_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_sorting_close_request (request_no),
  CONSTRAINT fk_sorting_close_check FOREIGN KEY (manifest_no) REFERENCES sorting_manifest_check(manifest_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sorting_close_outbox (
  manifest_no VARCHAR(64) NOT NULL,
  destination VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL,
  last_error VARCHAR(512) DEFAULT NULL,
  receipt_json JSON DEFAULT NULL,
  confirmed_at DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (manifest_no,destination),
  KEY idx_sorting_close_due (status,next_attempt_at),
  CONSTRAINT fk_sorting_close_outbox FOREIGN KEY (manifest_no) REFERENCES sorting_manifest_close(manifest_no),
  CONSTRAINT ck_sorting_close_destination CHECK (destination IN ('TRANSPORT','DISPATCH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE sorting_bag_position DROP CHECK ck_sorting_container_status;
ALTER TABLE sorting_bag_position ADD CONSTRAINT ck_sorting_container_status CHECK (container_status IN ('SEALED','OPENED','MISSING'));
ALTER TABLE sorting_bag_handoff DROP CHECK ck_sorting_handoff_status;
ALTER TABLE sorting_bag_handoff ADD CONSTRAINT ck_sorting_handoff_status CHECK (status IN ('RESERVED','RECEIVED','CANCELLED','MISSING'));
