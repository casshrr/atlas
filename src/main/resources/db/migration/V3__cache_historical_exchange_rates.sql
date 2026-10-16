CREATE TABLE exchange_rates (
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    purchase_date DATE NOT NULL,
    rate_date DATE NOT NULL,
    rate NUMERIC(30, 16) NOT NULL CHECK (rate > 0),
    source VARCHAR(50) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (from_currency, to_currency, purchase_date),
    CHECK (rate_date <= purchase_date)
);
