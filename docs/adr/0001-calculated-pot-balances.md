# Pot balances are calculated, not snapshotted

A Pot's balance for any month is calculated on read: start from its latest Balance Check-in on or before that month (or its opening balance), then add every Contribution and Allocation share since. We don't store a balance per month and there is no month-closing step, so future months show projected balances for free. Because this means a change to a past input silently changes past balances, Allocations are effective-dated (a change creates a new Allocation from that month rather than editing the old one), and Check-ins are the escape hatch when reality drifts from the projection.

## Considered Options

- **Month-end snapshots with a "close month" step**: rejected; adds a manual ritual and makes projections a separate feature.

## Consequences

- Editing or deleting a linked Expense still rewrites past Contributions, since Entries have no history. Accepted for now; a Check-in re-anchors the balance.
