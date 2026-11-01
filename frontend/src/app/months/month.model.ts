// Shapes of the JSON returned by the backend's EntryResponse and MonthSummaryResponse.
// Months are "yyyy-MM" strings, e.g. "2026-10".

export type EntryType = 'INCOME' | 'EXPENSE';

/**
 * A joint bank account that Expenses are paid out of and that needs money put into it each month.
 * Income lands in sole accounts the app doesn't track, so it has none (ADR 0002).
 */
export type Account = 'AIB' | 'REVOLUT';

/** Where forms start, and the Account an Expense row leaves unsaid because most are on it. */
export const DEFAULT_ACCOUNT: Account = 'AIB';

export const ACCOUNTS: readonly { value: Account; label: string }[] = [
  { value: 'AIB', label: 'AIB' },
  { value: 'REVOLUT', label: 'Revolut' },
];

export function accountLabel(account: Account): string {
  return ACCOUNTS.find((option) => option.value === account)!.label;
}

export interface Entry {
  id: number;
  type: EntryType;
  category: string;
  name: string | null;
  /** What the Entry comes to in the month returned; for a Weekly Rate, `weeklyRate × weeks`. */
  amount: number;
  /** An Expense's price per payday week; null for a monthly amount. */
  weeklyRate: number | null;
  /** The month's payday weeks (4 or 5) when there's a Weekly Rate; null otherwise. */
  weeks: number | null;
  /** The Account an Expense is paid out of; null for Income. */
  account: Account | null;
  startMonth: string;
  endMonth: string | null;
  /** The Pot this Expense is a Contribution to. */
  potId: number | null;
  /** The day of the month this Expense goes out, 1 to 31; null if not set, and always for Income. */
  dueDay: number | null;
  /** The Due Day as it falls in the month returned: the 31st is the 30th in November. */
  dueDayInMonth: number | null;
  createdAt: string;
}

/** Body of PUT /api/entries/{id}. The type and start month can't change after creation. */
export interface UpdateEntryRequest {
  category: string;
  name: string | null;
  /** The monthly amount; null when `weeklyRate` is given instead. */
  amount: number | null;
  /** An Expense's price per payday week; null for a monthly amount. Always null on Income. */
  weeklyRate: number | null;
  /** Required for an Expense; must be null for Income. */
  account: Account | null;
  oneOff: boolean;
  /** Makes an Expense a Contribution to this Pot; null for none. Always null on Income. */
  potId: number | null;
  /** 1 to 31, or null for none. Always null on Income. */
  dueDay: number | null;
}

/** Body of POST /api/entries. `startMonth` is the month being viewed. */
export interface CreateEntryRequest extends UpdateEntryRequest {
  type: EntryType;
  startMonth: string;
}

export interface MonthSummary {
  month: string;
  entries: Entry[];
  totalIncome: number;
  totalExpenses: number;
  remainder: number;
  /** One per Account, always: what to put into it this month. */
  transfers: Transfer[];
  /** The month's Expenses totalled per category, largest first. Variants like "groceries " are merged. */
  expensesByCategory: CategoryTotal[];
  /** Each Pot in use this month, with its end-of-month balance. */
  pots: PotBalance[];
  /** How this month's Remainder is split; null before the first Allocation starts. */
  allocation: AllocationSummary | null;
}

export interface CategoryTotal {
  category: string;
  amount: number;
}

export interface Transfer {
  account: Account;
  amount: number;
}

export type PotKind = 'SAVINGS' | 'LOAN' | 'OVERDRAFT' | 'CREDIT_CARD';

export const POT_KINDS: readonly { value: PotKind; label: string }[] = [
  { value: 'SAVINGS', label: 'Savings' },
  { value: 'LOAN', label: 'Loan' },
  { value: 'OVERDRAFT', label: 'Overdraft' },
  { value: 'CREDIT_CARD', label: 'Credit card' },
];

export function potKindLabel(kind: PotKind): string {
  return POT_KINDS.find((option) => option.value === kind)!.label;
}

/** A Debt Pot holds money owed; its balance is shown negative. */
export function isDebt(kind: PotKind): boolean {
  return kind !== 'SAVINGS';
}

/** Amounts are always positive, even for a debt. */
export interface Pot {
  id: number;
  name: string;
  kind: PotKind;
  account: Account;
  /** The balance at the end of `openingMonth`. */
  openingBalance: number;
  openingMonth: string;
  /** Hidden from this month onwards; null while in use. */
  archivedFrom: string | null;
}

/** Body of PUT /api/pots/{id}. The kind can't change after creation. */
export interface UpdatePotRequest {
  name: string;
  account: Account;
  openingBalance: number;
  openingMonth: string;
  archivedFrom: string | null;
}

/** Body of POST /api/pots. */
export interface CreatePotRequest {
  name: string;
  kind: PotKind;
  account: Account;
  openingBalance: number;
  openingMonth: string;
}

/** A Pot's balance at the end of one month: positive, even for a debt. */
export interface PotBalance {
  id: number;
  name: string;
  kind: PotKind;
  account: Account;
  balance: number;
  /** True when this month has a Balance Check-in rather than a calculated balance. */
  checkedIn: boolean;
}

/** One month's split of the Remainder. A null Pot means that share is unassigned. */
export interface AllocationSummary {
  /** The month this Allocation started. */
  effectiveFrom: string;
  savings: number;
  allowance: number;
  /** Whatever is left after Savings and Allowance; never below zero. */
  debtRepayment: number;
  /** Savings plus Allowance exceed the Remainder; neither is reduced. */
  shortfall: boolean;
  savingsPotId: number | null;
  savingsPotName: string | null;
  debtPotId: number | null;
  debtPotName: string | null;
  /** Debt Repayment the cleared Debt Pot couldn't take, sent to the Savings Pot instead. */
  debtOverflow: number;
  /** When the Debt Pot reached zero, if it's at zero this month. */
  debtPaidOffIn: string | null;
}

/** Body of PUT /api/months/{month}/allocation. */
export interface AllocationRequest {
  savings: number;
  allowance: number;
  savingsPotId: number | null;
  debtPotId: number | null;
}
