CREATE TABLE pots (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(50)    NOT NULL,
    kind            VARCHAR(20)    NOT NULL,
    account         VARCHAR(20)    NOT NULL,
    opening_balance DECIMAL(12, 2) NOT NULL,
    opening_month   DATE           NOT NULL,
    archived_from   DATE           NULL,
    CONSTRAINT chk_pots_kind            CHECK (kind IN ('SAVINGS', 'LOAN', 'OVERDRAFT', 'CREDIT_CARD')),
    CONSTRAINT chk_pots_account         CHECK (account IN ('AIB', 'REVOLUT')),
    CONSTRAINT chk_pots_opening_balance CHECK (opening_balance >= 0)
);

CREATE TABLE balance_check_ins (
    id      BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    pot_id  BIGINT         NOT NULL,
    month   DATE           NOT NULL,
    balance DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_balance_check_ins_pot     FOREIGN KEY (pot_id) REFERENCES pots (id),
    CONSTRAINT uq_balance_check_ins_month   UNIQUE (pot_id, month),
    CONSTRAINT chk_balance_check_ins_balance CHECK (balance >= 0)
);

ALTER TABLE entries
    ADD COLUMN pot_id BIGINT NULL,
    ADD CONSTRAINT fk_entries_pot FOREIGN KEY (pot_id) REFERENCES pots (id),
    ADD CONSTRAINT chk_entries_pot_expense CHECK (pot_id IS NULL OR type = 'EXPENSE');
