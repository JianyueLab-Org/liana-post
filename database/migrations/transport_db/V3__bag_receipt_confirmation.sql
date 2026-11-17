ALTER TABLE transport_manifest_assignment
  DROP CHECK ck_transport_assignment_status;
ALTER TABLE transport_manifest_assignment
  ADD COLUMN received_at DATETIME(3) DEFAULT NULL;
ALTER TABLE transport_manifest_assignment
  ADD CONSTRAINT ck_transport_assignment_status CHECK (status IN ('PENDING','CONFIRMED','RELEASING','RELEASED','CONFLICT','RECEIVED'));

CREATE TABLE transport_receipt_inbox (
  event_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  assignment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  bag_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  manifest_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_json JSON NOT NULL,
  receipt_json JSON NOT NULL,
  confirmed_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_transport_receipt_bag (assignment_no, bag_no),
  CONSTRAINT fk_transport_receipt_bag FOREIGN KEY (assignment_no, bag_no) REFERENCES transport_assignment_bag(assignment_no, bag_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
