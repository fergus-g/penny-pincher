# Payments are stored in the backend, current month only

Ticking an AIB Expense as paid is a manual check against the bank, and it only matters for the month in hand. We store each Payment as a backend row (Expense and month) rather than in the browser, and only the current month can be ticked: earlier months are assumed fully paid and later months are unpaid.

## Considered Options

- **localStorage keyed by month**: no backend work. Rejected: ticks would live in one browser, so once the app is deployed they wouldn't follow you between phone and laptop, and the other person in the household wouldn't see them.
- **A paid flag on the Entry**: Rejected: an Entry spans many months, so the flag would need resetting each month.

## Consequences

- Only AIB Expenses can have a Payment; Revolut is tracked by its Transfer alone.
- A new month starts with no Payments, so nothing needs clearing.
- Payments for past months stay in the table but are ignored; the app treats those months as paid.
