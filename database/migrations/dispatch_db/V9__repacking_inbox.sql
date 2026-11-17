CREATE TABLE dispatch_repack_inbox (
  request_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  bag_no VARCHAR(32) NOT NULL,
  payload_json JSON NOT NULL,
  receipt_json JSON NOT NULL,
  prepared_at DATETIME(3) NOT NULL,
  PRIMARY KEY (request_no),
  UNIQUE KEY uk_dispatch_repack_bag (bag_no),
  CONSTRAINT fk_dispatch_repack_bag FOREIGN KEY (bag_no) REFERENCES dispatch_bag(bag_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dispatch_repack_source (
  bag_no VARCHAR(32) NOT NULL,
  mail_no VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_bag_no VARCHAR(32) NOT NULL,
  unpack_event_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (bag_no,mail_no),
  UNIQUE KEY uk_dispatch_repack_scan (unpack_event_no,mail_no),
  CONSTRAINT fk_dispatch_repack_source FOREIGN KEY (bag_no,mail_no) REFERENCES dispatch_bag_item(bag_no,item_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
