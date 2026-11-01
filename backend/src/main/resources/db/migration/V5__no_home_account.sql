-- Accounts are joint, and Income lands in sole accounts the app doesn't track (ADR 0002).

-- Income has no Account; every Expense still has one.
ALTER TABLE entries MODIFY account VARCHAR(20) NULL;
UPDATE entries SET account = NULL WHERE type = 'INCOME';
ALTER TABLE entries
    ADD CONSTRAINT chk_entries_account_expense_only
        CHECK ((type = 'INCOME' AND account IS NULL) OR (type = 'EXPENSE' AND account IS NOT NULL));

-- The Allocation no longer feeds Transfers, so its Allowance needs no Account.
ALTER TABLE allocations DROP CHECK chk_allocations_account;
ALTER TABLE allocations DROP COLUMN allowance_account;
