-- Additive tables for recoverable single-mail settlement; timestamps are UTC epoch milliseconds.
CREATE TABLE IF NOT EXISTS postage_acceptance_lock (id INT PRIMARY KEY);
INSERT INTO postage_acceptance_lock (id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM postage_acceptance_lock WHERE id=1);
CREATE TABLE IF NOT EXISTS postage_acceptance (
    acceptance_id VARCHAR(36) PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    quote_id VARCHAR(36) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    request_json LONGTEXT NOT NULL,
    mail_json LONGTEXT NOT NULL,
    traditional_amount DECIMAL(12,2) NOT NULL,
    digital_amount DECIMAL(12,2) NOT NULL,
    applied_amount DECIMAL(12,2) NOT NULL,
    excess_amount DECIMAL(12,2) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    facility_code VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    completed_at BIGINT,
    waybill_no VARCHAR(32) UNIQUE,
    UNIQUE (created_by, request_id),
    FOREIGN KEY (quote_id) REFERENCES postage_quote(quote_id),
    INDEX idx_postage_acceptance_creator (created_by,created_at)
);
CREATE TABLE IF NOT EXISTS postage_acceptance_stamp (
    acceptance_id VARCHAR(36) NOT NULL,
    stamp_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (acceptance_id, stamp_id),
    FOREIGN KEY (acceptance_id) REFERENCES postage_acceptance(acceptance_id),
    FOREIGN KEY (stamp_id) REFERENCES postage_digital_stamp(stamp_id),
    INDEX idx_postage_acceptance_stamp (stamp_id)
);
