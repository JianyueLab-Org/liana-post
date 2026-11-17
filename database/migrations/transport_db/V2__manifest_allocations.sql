-- New task writes use manifest allocations. Legacy task_bag rows remain immutable history.
CREATE TABLE transport_manifest_assignment (
  assignment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  manifest_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  version_no INT NOT NULL,
  content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  task_id BIGINT NOT NULL,
  source_org_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  destination_org_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  facility_route_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  snapshot_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  actor_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  created_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) DEFAULT NULL,
  released_at DATETIME(3) DEFAULT NULL,
  CONSTRAINT fk_transport_assignment_task FOREIGN KEY (task_id) REFERENCES transport_task(id),
  KEY idx_transport_assignment_task (task_id, assignment_no),
  CONSTRAINT ck_transport_assignment_status CHECK (status IN ('PENDING','CONFIRMED','RELEASING','RELEASED','CONFLICT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transport_assignment_bag (
  assignment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  bag_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  dispatch_bag_id BIGINT NOT NULL,
  weight_grams INT DEFAULT NULL,
  PRIMARY KEY (assignment_no, bag_no),
  CONSTRAINT fk_transport_assignment_bag FOREIGN KEY (assignment_no) REFERENCES transport_manifest_assignment(assignment_no),
  CONSTRAINT ck_transport_assignment_weight CHECK (weight_grams IS NULL OR weight_grams>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transport_manifest_current (
  manifest_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  assignment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  UNIQUE KEY uk_transport_current_assignment (assignment_no),
  CONSTRAINT fk_transport_current_assignment FOREIGN KEY (assignment_no) REFERENCES transport_manifest_assignment(assignment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transport_bag_current (
  bag_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  assignment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  CONSTRAINT fk_transport_current_bag FOREIGN KEY (assignment_no, bag_no) REFERENCES transport_assignment_bag(assignment_no, bag_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transport_assignment_outbox (
  request_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  assignment_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL,
  last_error VARCHAR(512) DEFAULT NULL,
  receipt_json JSON DEFAULT NULL,
  created_at DATETIME(3) NOT NULL,
  confirmed_at DATETIME(3) DEFAULT NULL,
  UNIQUE KEY uk_transport_assignment_action (assignment_no, action),
  KEY idx_transport_assignment_retry (status, next_attempt_at),
  CONSTRAINT fk_transport_outbox_assignment FOREIGN KEY (assignment_no) REFERENCES transport_manifest_assignment(assignment_no),
  CONSTRAINT ck_transport_assignment_action CHECK (action IN ('ASSIGN','RELEASE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
