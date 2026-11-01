ALTER TABLE entries
    ADD COLUMN account VARCHAR(20) NOT NULL DEFAULT 'AIB',
    ADD CONSTRAINT chk_entries_account CHECK (account IN ('AIB', 'REVOLUT'));
