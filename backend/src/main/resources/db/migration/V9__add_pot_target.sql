-- A Savings Pot's target, and an edited monthly amount that replaces the one worked out from its
-- Contributions and the Allocation. Debt Pots have neither: their target is always zero.
ALTER TABLE pots
    ADD COLUMN target         DECIMAL(12, 2) NULL,
    ADD COLUMN monthly_amount DECIMAL(12, 2) NULL,
    ADD CONSTRAINT chk_pots_target          CHECK (target IS NULL OR target > 0),
    ADD CONSTRAINT chk_pots_monthly_amount  CHECK (monthly_amount IS NULL OR monthly_amount >= 0),
    ADD CONSTRAINT chk_pots_target_savings  CHECK ((target IS NULL AND monthly_amount IS NULL) OR kind = 'SAVINGS');
