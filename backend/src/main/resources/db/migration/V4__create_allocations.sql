CREATE TABLE allocations (
    id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    effective_from    DATE           NOT NULL,
    savings           DECIMAL(12, 2) NOT NULL,
    allowance         DECIMAL(12, 2) NOT NULL,
    allowance_account VARCHAR(20)    NOT NULL,
    savings_pot_id    BIGINT         NULL,
    debt_pot_id       BIGINT         NULL,
    CONSTRAINT uq_allocations_effective_from UNIQUE (effective_from),
    CONSTRAINT chk_allocations_savings       CHECK (savings >= 0),
    CONSTRAINT chk_allocations_allowance     CHECK (allowance >= 0),
    CONSTRAINT chk_allocations_account       CHECK (allowance_account IN ('AIB', 'REVOLUT')),
    CONSTRAINT fk_allocations_savings_pot    FOREIGN KEY (savings_pot_id) REFERENCES pots (id),
    CONSTRAINT fk_allocations_debt_pot       FOREIGN KEY (debt_pot_id) REFERENCES pots (id)
);
