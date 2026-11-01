# Penny Pincher

A personal monthly budget tracker: money coming in and going out each month, and what's left.

## Language

### Entries

**Entry**:
A single amount of money coming in or going out, active in one or more months. An Entry is permanently either Income or an Expense.
_Avoid_: Transaction, item, line

**Income**:
An Entry for money coming in.
_Avoid_: Earnings, credit

**Expense**:
An Entry for money going out.
_Avoid_: Outgoing, cost, debit

**Account**:
The bank account an Entry is paid out of or lands in (currently AIB or Revolut).
_Avoid_: Target, transferTo, bank

**Transfer**:
The amount that must be moved into an Account in a month to cover what goes out of it.
_Avoid_: Top-up, movement

**Remainder**:
Total Income minus total Expenses for a month. Shown to users as "Left over this month".
_Avoid_: Balance, left over, surplus

### Allocation

**Allocation**:
A standing plan, effective from a given month, for splitting the Remainder into Savings, Allowance and Debt Repayment.
_Avoid_: Cashflow, budget, split

**Savings**:
The fixed part of an Allocation set aside to grow savings.
_Avoid_: Saving value

**Allowance**:
The fixed part of an Allocation kept as personal spending money.
_Avoid_: Personal income, pocket money

**Debt Repayment**:
Whatever is left of the Remainder after Savings and Allowance, put towards clearing debt. Never below zero.
_Avoid_: Debt clearing, overpayment

**Shortfall**:
A month where Savings plus Allowance exceed the Remainder.
_Avoid_: Deficit, overspend

### Pots

**Pot**:
A running balance tracked month by month: either money saved or money owed.
_Avoid_: Account, fund, holding

**Savings Pot**:
A Pot holding money saved.
_Avoid_: Savings account

**Debt Pot**:
A Pot holding money owed: a loan, overdraft or credit card. Shown as a negative balance.
_Avoid_: Liability, negative savings

**Contribution**:
An Expense linked to a Pot: it adds to a Savings Pot or pays down a Debt Pot in every month it is active.
_Avoid_: Payment, deposit

**Balance Check-in**:
The actual balance of a Pot in a given month, entered by hand; later months are calculated from it.
_Avoid_: Adjustment, correction, reconciliation
