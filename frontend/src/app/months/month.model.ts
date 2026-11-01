// Shapes of the JSON returned by the backend's EntryResponse and MonthSummaryResponse.
// Months are "yyyy-MM" strings, e.g. "2026-10".

export type EntryType = 'INCOME' | 'EXPENSE';

export interface Entry {
  id: number;
  type: EntryType;
  category: string;
  name: string | null;
  amount: number;
  startMonth: string;
  endMonth: string | null;
  createdAt: string;
}

/** Body of PUT /api/entries/{id}. The type and start month can't change after creation. */
export interface UpdateEntryRequest {
  category: string;
  name: string | null;
  amount: number;
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
}
