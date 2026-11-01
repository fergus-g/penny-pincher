// Shapes of the JSON returned by the backend's EntryResponse and MonthSummaryResponse.
// Months are "yyyy-MM" strings, e.g. "2026-10".

export type EntryType = 'INCOME' | 'EXPENSE';

/** The bank account an Entry is paid out of or lands in. */
export type Account = 'AIB' | 'REVOLUT';

/** Income lands here; Transfers move money from it to the other Accounts. */
export const HOME_ACCOUNT: Account = 'AIB';

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
  amount: number;
  account: Account;
  startMonth: string;
  endMonth: string | null;
  /** The Pot this Expense is a Contribution to. */
  potId: number | null;
  createdAt: string;
}

/** Body of PUT /api/entries/{id}. The type and start month can't change after creation. */
export interface UpdateEntryRequest {
  category: string;
  name: string | null;
  amount: number;
  account: Account;
  oneOff: boolean;
  /** Makes an Expense a Contribution to this Pot; null for none. Always null on Income. */
  potId: number | null;
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
  /** One per non-home Account: what to move into it this month. Negative means move it back home. */
  transfers: Transfer[];
  /** The month's Expenses totalled per category, largest first. Variants like "groceries " are merged. */
  expensesByCategory: CategoryTotal[];
  /** Each Pot in use this month, with its end-of-month balance. */
  pots: PotBalance[];
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
