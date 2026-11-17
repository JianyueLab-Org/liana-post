CREATE TABLE dispatch_manifest_assignment (
  manifest_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  assignment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  task_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  version_no INT NOT NULL,
  content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  confirmed_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_dispatch_assignment_no (assignment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dispatch_assignment_inbox (
  request_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  assignment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  manifest_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_json JSON NOT NULL,
  receipt_json JSON NOT NULL,
  confirmed_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_dispatch_assignment_action (assignment_no, action),
  KEY idx_dispatch_assignment_history (manifest_no, confirmed_at, request_no),
  CONSTRAINT ck_dispatch_assignment_action CHECK (action IN ('ASSIGN','RELEASE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
