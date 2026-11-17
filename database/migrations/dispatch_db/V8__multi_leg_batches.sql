-- Packing endpoints stay on dispatch_bag; these fields describe this batch's transport leg.
ALTER TABLE dispatch_bag ADD COLUMN last_receipt_event_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;

ALTER TABLE dispatch_batch
  ADD COLUMN source_org_code VARCHAR(64) DEFAULT NULL,
  ADD COLUMN destination_org_code VARCHAR(64) DEFAULT NULL,
  ADD COLUMN facility_route_code VARCHAR(64) DEFAULT NULL;

ALTER TABLE dispatch_batch_bag
  DROP INDEX uk_dispatch_batch_bag,
  ADD COLUMN active_flag TINYINT DEFAULT 1,
  ADD UNIQUE KEY uk_dispatch_batch_bag_history (batch_no, bag_no),
  ADD UNIQUE KEY uk_dispatch_bag_active_batch (bag_no, active_flag),
  ADD CONSTRAINT ck_dispatch_batch_bag_active CHECK (active_flag IS NULL OR active_flag=1);

-- Earlier V7 allows only one leg per bag. Preserve its receipts while ending confirmed custody.
UPDATE dispatch_bag b JOIN dispatch_receipt_inbox r ON r.bag_no=b.bag_no
  SET b.last_receipt_event_no=r.event_no,b.transport_task_code=NULL
  WHERE b.status='ARRIVED';
UPDATE dispatch_batch_bag bb JOIN dispatch_batch d ON d.batch_no=bb.batch_no
  LEFT JOIN dispatch_receipt_inbox r ON r.manifest_no=d.manifest_no AND r.bag_no=bb.bag_no
  SET bb.active_flag=NULL WHERE d.status='CANCELLED' OR r.event_no IS NOT NULL;

CREATE TABLE dispatch_forward_inbox (
  request_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  manifest_no VARCHAR(64) NOT NULL,
  previous_manifest_no VARCHAR(64) NOT NULL,
  payload_json JSON NOT NULL,
  receipt_json JSON NOT NULL,
  confirmed_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_dispatch_forward_manifest (manifest_no),
  CONSTRAINT fk_dispatch_forward_batch FOREIGN KEY (manifest_no) REFERENCES dispatch_batch(manifest_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
