-- Pending packing is confirmed by OMS before batching. JSON is retained only as a compatibility snapshot.
ALTER TABLE dispatch_bag MODIFY COLUMN total_weight_grams INT DEFAULT NULL;

CREATE TABLE dispatch_bag_packing (
  bag_no VARCHAR(32) NOT NULL,
  packing_version INT NOT NULL,
  event_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_json JSON NOT NULL,
  sync_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL,
  last_error VARCHAR(512) DEFAULT NULL,
  recorded_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (bag_no),
  UNIQUE KEY uk_bag_packing_event (event_no),
  KEY idx_bag_packing_retry (sync_status, next_attempt_at),
  CONSTRAINT fk_bag_packing_bag FOREIGN KEY (bag_no) REFERENCES dispatch_bag (bag_no),
  CONSTRAINT ck_bag_packing_version CHECK (packing_version = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dispatch_bag_item (
  bag_no VARCHAR(32) NOT NULL,
  item_no VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  seq_no INT NOT NULL,
  weight_grams INT DEFAULT NULL,
  PRIMARY KEY (bag_no, item_no),
  UNIQUE KEY uk_bag_item_sequence (bag_no, seq_no),
  KEY idx_bag_item_history (item_no),
  CONSTRAINT fk_bag_item_packing FOREIGN KEY (bag_no) REFERENCES dispatch_bag_packing (bag_no),
  CONSTRAINT ck_bag_item_sequence CHECK (seq_no > 0),
  CONSTRAINT ck_bag_item_weight CHECK (weight_grams IS NULL OR weight_grams >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
