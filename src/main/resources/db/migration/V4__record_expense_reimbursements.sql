ALTER TABLE expenses ADD CONSTRAINT uq_expenses_id_currency UNIQUE (id, currency);
CREATE TABLE reimbursements (
    id UUID PRIMARY KEY,
    expense_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    received_on DATE NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (expense_id, currency) REFERENCES expenses(id, currency) ON DELETE RESTRICT
);
CREATE INDEX idx_reimbursements_expense ON reimbursements(expense_id);
