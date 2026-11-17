-- Audited empty-schema baseline. No legacy business rows. UTC DATETIME(3).
-- Provenance and retained legacy contracts: database/inventory.md

CREATE TABLE postage_tariff_lock (
    id INT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postage_tariff_version (
    version_id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    revision INT NOT NULL,
    effective_from BIGINT,
    definition_json LONGTEXT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at BIGINT NOT NULL,
    published_by VARCHAR(128),
    published_at BIGINT,
    UNIQUE (effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postage_quote (
    quote_id VARCHAR(36) PRIMARY KEY,
    tariff_version VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    FOREIGN KEY (tariff_version) REFERENCES postage_tariff_version(version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postage_stamp_policy (
    id INT PRIMARY KEY,
    revision INT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    min_face_value DECIMAL(8,2) NOT NULL,
    max_face_value DECIMAL(8,2) NOT NULL,
    validity_days INT NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postage_sale (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postage_receipt (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postage_digital_stamp (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postage_retail_audit (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postage_acceptance_lock (id INT PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postage_acceptance (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE postage_acceptance_stamp (
    acceptance_id VARCHAR(36) NOT NULL,
    stamp_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (acceptance_id, stamp_id),
    FOREIGN KEY (acceptance_id) REFERENCES postage_acceptance(acceptance_id),
    FOREIGN KEY (stamp_id) REFERENCES postage_digital_stamp(stamp_id),
    INDEX idx_postage_acceptance_stamp (stamp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE demo_seed_history (
  seed_version VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL,
  applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
