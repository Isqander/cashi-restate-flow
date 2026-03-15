CREATE TABLE IF NOT EXISTS fee_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  VARCHAR(255)   NOT NULL,
    amount          NUMERIC(19,4)  NOT NULL,
    asset           VARCHAR(10)    NOT NULL,
    asset_type      VARCHAR(20)    NOT NULL,
    transaction_type VARCHAR(50)   NOT NULL,
    fee             NUMERIC(19,4)  NOT NULL,
    rate            NUMERIC(10,6)  NOT NULL,
    description     VARCHAR(500)   NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_fee_transaction_idempotent
    ON fee_records(transaction_id);
