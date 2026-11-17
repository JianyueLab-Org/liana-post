-- Additive schema; safe to re-run. UTC epoch milliseconds, decimal LNR amounts.
CREATE TABLE IF NOT EXISTS postage_stamp_policy (
    id INT PRIMARY KEY,
    revision INT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    min_face_value DECIMAL(8,2) NOT NULL,
    max_face_value DECIMAL(8,2) NOT NULL,
    validity_days INT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at BIGINT NOT NULL
);
INSERT INTO postage_stamp_policy (id,revision,currency,min_face_value,max_face_value,validity_days,updated_by,updated_at)
SELECT 1,1,'LNR',0.01,100.00,365,'SYSTEM',0
WHERE NOT EXISTS (SELECT 1 FROM postage_stamp_policy WHERE id=1);

CREATE TABLE IF NOT EXISTS postage_sale (
    sale_id VARCHAR(36) PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    lines_json LONGTEXT NOT NULL,
    policy_revision INT NOT NULL,
    validity_days INT NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    facility_code VARCHAR(64),
    created_at BIGINT NOT NULL,
    paid_at BIGINT,
    UNIQUE (created_by,request_id),
    INDEX idx_postage_sale_creator (created_by,created_at)
);

CREATE TABLE IF NOT EXISTS postage_receipt (
    receipt_id VARCHAR(36) PRIMARY KEY,
    sale_id VARCHAR(36) NOT NULL UNIQUE,
    request_id VARCHAR(64) NOT NULL,
    method VARCHAR(16) NOT NULL,
    amount_received DECIMAL(12,2) NOT NULL,
    confirmed_by VARCHAR(128) NOT NULL,
    facility_code VARCHAR(64),
    confirmed_at BIGINT NOT NULL,
    UNIQUE (confirmed_by,request_id),
    FOREIGN KEY (sale_id) REFERENCES postage_sale(sale_id)
);

CREATE TABLE IF NOT EXISTS postage_digital_stamp (
    stamp_id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    sale_id VARCHAR(36) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    face_value DECIMAL(8,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    issued_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    voided_by VARCHAR(128),
    voided_at BIGINT,
    void_reason VARCHAR(500),
    void_request_id VARCHAR(64),
    FOREIGN KEY (sale_id) REFERENCES postage_sale(sale_id),
    INDEX idx_postage_stamp_sale (sale_id)
);

CREATE TABLE IF NOT EXISTS postage_retail_audit (
    event_id VARCHAR(36) PRIMARY KEY,
    subject_type VARCHAR(16) NOT NULL,
    subject_id VARCHAR(36) NOT NULL,
    sale_id VARCHAR(36),
    event_type VARCHAR(32) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    facility_code VARCHAR(64),
    occurred_at BIGINT NOT NULL,
    detail LONGTEXT NOT NULL,
    INDEX idx_postage_audit_sale (sale_id,occurred_at)
);
