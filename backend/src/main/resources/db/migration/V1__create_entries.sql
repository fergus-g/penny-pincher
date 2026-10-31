CREATE TABLE entries (
    id          BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type        VARCHAR(10)    NOT NULL,
    category    VARCHAR(50)    NOT NULL,
    name        VARCHAR(100)   NULL,
    amount      DECIMAL(12, 2) NOT NULL,
    start_month DATE           NOT NULL,
    end_month   DATE           NULL,
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_entries_type   CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_entries_amount CHECK (amount > 0)
);
