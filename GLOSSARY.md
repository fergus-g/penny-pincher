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
A joint bank account that Expenses are paid out of (currently AIB or Revolut). Income has no Account: it lands in sole accounts the app doesn't track. No Account is "home" (ADR 0002).
_Avoid_: Target, transferTo, bank, home account

**Transfer**:
The amount to put into an Account in a month: the total of the Expenses paid out of it. Every Account has one each month, even at zero.
_Avoid_: Top-up, movement

**Due Day**:
The day of the month an Expense goes out of its Account, from 1 to 31. In a shorter month it falls on the last day (the 31st is the 30th in November). Optional; Income has none.
_Avoid_: Due date, payment day

**Weekly Rate**:
An Expense priced per week instead of per month. Its amount in a month is the rate times the weeks from the last Thursday of the previous month to the last Thursday of that month (payday to payday), always 4 or 5.
_Avoid_: Weekly amount, per-week cost

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
The fixed part of an Allocation kept as personal spending money. It has no Account and isn't part of any Transfer.
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
