-- The day of the month an Expense goes out of its Account. Income has none.
ALTER TABLE entries
    ADD COLUMN due_day TINYINT NULL,
    ADD CONSTRAINT chk_entries_due_day CHECK (due_day BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_entries_due_day_expense_only CHECK (due_day IS NULL OR type = 'EXPENSE');
