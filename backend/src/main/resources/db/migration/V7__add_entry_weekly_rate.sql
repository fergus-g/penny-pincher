-- An Expense priced per week: its amount in a month is the rate times that month's payday weeks.
-- Every Entry has exactly one of a monthly amount or a weekly rate; only Expenses can be weekly.
ALTER TABLE entries
    MODIFY amount DECIMAL(12, 2) NULL,
    ADD COLUMN weekly_rate DECIMAL(12, 2) NULL,
    ADD CONSTRAINT chk_entries_weekly_rate CHECK (weekly_rate > 0),
    ADD CONSTRAINT chk_entries_one_amount CHECK ((amount IS NULL) <> (weekly_rate IS NULL)),
    ADD CONSTRAINT chk_entries_weekly_rate_expense_only CHECK (weekly_rate IS NULL OR type = 'EXPENSE');
