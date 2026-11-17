-- Create postage_db separately. All times below are UTC epoch milliseconds.
CREATE TABLE IF NOT EXISTS postage_tariff_lock (
    id INT PRIMARY KEY
);
INSERT INTO postage_tariff_lock (id)
SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM postage_tariff_lock WHERE id = 1);

CREATE TABLE IF NOT EXISTS postage_tariff_version (
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
);

CREATE TABLE IF NOT EXISTS postage_quote (
    quote_id VARCHAR(36) PRIMARY KEY,
    tariff_version VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    FOREIGN KEY (tariff_version) REFERENCES postage_tariff_version(version_id)
);
