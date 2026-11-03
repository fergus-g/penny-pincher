-- A Payment ticks an AIB Expense as paid in one month (ADR 0003). Deleting the Entry deletes its Payments.
CREATE TABLE payments (
    id       BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    entry_id BIGINT    NOT NULL,
    month    DATE      NOT NULL,
    paid_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_entry    FOREIGN KEY (entry_id) REFERENCES entries (id) ON DELETE CASCADE,
    CONSTRAINT uq_payments_month    UNIQUE (entry_id, month)
);
