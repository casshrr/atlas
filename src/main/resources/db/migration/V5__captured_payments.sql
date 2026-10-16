CREATE TABLE captured_payments (
    id UUID PRIMARY KEY,
    payload_hash VARCHAR(64) NOT NULL,
    source VARCHAR(100) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    time_zone VARCHAR(100) NOT NULL,
    raw_text VARCHAR(4000) NOT NULL,
    amount NUMERIC(19,2),
    currency VARCHAR(3),
    merchant VARCHAR(200),
    category VARCHAR(50),
    spent_on DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('REVIEW','IMPORTED','DISMISSED')),
    reason VARCHAR(1000) NOT NULL,
    expense_id UUID REFERENCES expenses(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX captured_payments_inbox ON captured_payments(status, created_at DESC);
CREATE INDEX captured_payments_hash ON captured_payments(payload_hash);
CREATE TABLE merchant_categories (
    merchant_key VARCHAR(310) PRIMARY KEY,
    category VARCHAR(50) NOT NULL
);
