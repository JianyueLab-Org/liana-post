USE oms_db;
-- Additive OMS ledger. Must remain in sync with the service's acceptance-schema.sql.
CREATE TABLE IF NOT EXISTS oms_postage_acceptance (
    acceptance_id VARCHAR(36) PRIMARY KEY,
    created_by VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    waybill_no VARCHAR(32) UNIQUE,
    updated_at BIGINT NOT NULL
);
