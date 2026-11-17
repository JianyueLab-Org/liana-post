-- The first accepted seal is an immutable receipt, retained after receiving starts.
CREATE TABLE sorting_manifest_seal (
  manifest_no VARCHAR(64) NOT NULL,
  version_no INT NOT NULL,
  request_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_json JSON NOT NULL,
  content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_org_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  actor_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  sealed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (manifest_no),
  UNIQUE KEY uk_manifest_seal_request (request_no),
  CONSTRAINT fk_manifest_seal_version FOREIGN KEY (manifest_no, version_no)
    REFERENCES sorting_manifest_version (manifest_no, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
