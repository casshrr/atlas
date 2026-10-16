CREATE TABLE expenses
(
    id UUID PRIMARY KEY,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    spent_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);