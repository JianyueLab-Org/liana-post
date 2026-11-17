-- Current detail tables are projections. Every accepted prealert retains its complete immutable content.
ALTER TABLE sorting_manifest
  ADD COLUMN version_no INT NOT NULL DEFAULT 0,
  ADD COLUMN content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;

ALTER TABLE sorting_manifest_item MODIFY COLUMN expected_route_code VARCHAR(64) DEFAULT NULL;

CREATE TABLE sorting_manifest_version (
  manifest_no VARCHAR(64) NOT NULL,
  version_no INT NOT NULL,
  content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  expected_package_qty INT NOT NULL,
  expected_item_qty INT NOT NULL,
  snapshot_json JSON NOT NULL,
  recorded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (manifest_no, version_no),
  CONSTRAINT fk_manifest_version_manifest FOREIGN KEY (manifest_no) REFERENCES sorting_manifest (manifest_no),
  CONSTRAINT ck_manifest_version_positive CHECK (version_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
