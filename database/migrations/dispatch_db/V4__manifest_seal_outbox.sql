ALTER TABLE dispatch_manifest_outbox
  ADD COLUMN event_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PREALERT',
  ADD COLUMN receipt_json JSON DEFAULT NULL,
  DROP INDEX uk_dispatch_manifest_outbox_version,
  ADD UNIQUE KEY uk_dispatch_manifest_outbox_action (manifest_no, version_no, event_type),
  ADD CONSTRAINT ck_dispatch_manifest_outbox_action CHECK (event_type IN ('PREALERT', 'SEAL'));
