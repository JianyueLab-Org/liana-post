ALTER TABLE dispatch_manifest_outbox
  DROP CHECK ck_dispatch_manifest_outbox_action,
  ADD CONSTRAINT ck_dispatch_manifest_outbox_action
    CHECK (event_type IN ('PREALERT', 'SEAL', 'CANCEL'));
