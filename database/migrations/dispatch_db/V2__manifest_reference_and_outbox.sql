ALTER TABLE dispatch_batch
  ADD COLUMN manifest_no VARCHAR(64) DEFAULT NULL,
  ADD UNIQUE KEY uk_dispatch_batch_manifest (manifest_no);

CREATE TABLE dispatch_manifest_outbox (
  event_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  manifest_no VARCHAR(64) NOT NULL,
  version_no INT NOT NULL,
  payload_json JSON NOT NULL,
  content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_error VARCHAR(512) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  sent_at DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (event_no),
  UNIQUE KEY uk_dispatch_manifest_outbox_version (manifest_no, version_no),
  KEY idx_dispatch_manifest_outbox_retry (status, next_attempt_at),
  CONSTRAINT ck_dispatch_manifest_outbox_version CHECK (version_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
