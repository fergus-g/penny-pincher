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
  createdAt: string;
}

/** Body of PUT /api/entries/{id}. The type and start month can't change after creation. */
export interface UpdateEntryRequest {
  category: string;
  name: string | null;
  amount: number;
  account: Account;
  oneOff: boolean;
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
}

export interface CategoryTotal {
  category: string;
  amount: number;
}

export interface Transfer {
  account: Account;
  amount: number;
}
