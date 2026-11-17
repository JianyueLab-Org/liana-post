ALTER TABLE transport_manifest_assignment DROP CHECK ck_transport_assignment_status;
ALTER TABLE transport_manifest_assignment ADD CONSTRAINT ck_transport_assignment_status CHECK (status IN ('PENDING','CONFIRMED','RELEASING','RELEASED','CONFLICT','RECEIVED','SETTLED'));

CREATE TABLE transport_manifest_close_inbox (
  manifest_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  request_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  assignment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_json JSON NOT NULL,
  receipt_json JSON NOT NULL,
  confirmed_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_transport_close_request (request_no),
  CONSTRAINT fk_transport_close_assignment FOREIGN KEY (assignment_no) REFERENCES transport_manifest_assignment(assignment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
