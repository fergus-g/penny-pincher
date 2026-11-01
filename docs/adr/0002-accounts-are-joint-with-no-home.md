# Accounts are joint, with no home Account

AIB and Revolut are both joint accounts that Expenses are paid out of, and both need money put into them every month. Income is paid into sole accounts the app doesn't track. So there is no home Account that Income lands in and other Accounts are topped up from: Income has no Account at all, and each month's Transfers are simply "put this much into each Account", where each amount is the total of the Expenses paid out of that Account.

## Considered Options

- **AIB as the home Account** (v2): Income landed in AIB and only Revolut needed a Transfer, sometimes negative ("move it back to AIB"). Rejected: it modelled an account setup we don't have, and it hid what AIB itself needs each month.
- **Allocation shares in Transfers** (v2): Savings, Allowance and Debt Repayment were added to the Transfer of the Account they go to. Rejected: those moves happen from the sole accounts, not into the joint ones, so they don't belong in what the joint accounts need.

## Consequences

- Income's Account values are dropped, and an Expense must have one; the database enforces both.
- The Allocation's Allowance no longer has an Account.
- Pots keep their Account purely as a label of where the money is held; it doesn't affect any Transfer.
