import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AllocationRequest,
  CreateEntryRequest,
  CreatePotRequest,
  Entry,
  EntryType,
  MonthSummary,
  Pot,
  UpdateEntryRequest,
  UpdatePotRequest,
} from './month.model';

@Service()
export class MonthApi {
  private readonly http = inject(HttpClient);

  getMonth(month: string): Observable<MonthSummary> {
    console.log('[MonthApi] getMonth', month);
    return this.http.get<MonthSummary>(`/api/months/${month}`);
  }

  /** Every category used on this type of Entry, in any month: autocomplete suggestions. */
  getCategories(type: EntryType): Observable<string[]> {
    console.log('[MonthApi] getCategories', type);
    return this.http.get<string[]>('/api/categories', { params: { type } });
  }

  createEntry(request: CreateEntryRequest): Observable<Entry> {
    console.log('[MonthApi] createEntry', request);
    return this.http.post<Entry>('/api/entries', request);
  }

  updateEntry(id: number, request: UpdateEntryRequest): Observable<Entry> {
    console.log('[MonthApi] updateEntry', id, request);
    return this.http.put<Entry>(`/api/entries/${id}`, request);
  }

  deleteEntry(id: number): Observable<void> {
    console.log('[MonthApi] deleteEntry', id);
    return this.http.delete<void>(`/api/entries/${id}`);
  }

  /** Every Pot, archived ones included. */
  getPots(): Observable<Pot[]> {
    console.log('[MonthApi] getPots');
    return this.http.get<Pot[]>('/api/pots');
  }

  createPot(request: CreatePotRequest): Observable<Pot> {
    console.log('[MonthApi] createPot', request);
    return this.http.post<Pot>('/api/pots', request);
  }

  updatePot(id: number, request: UpdatePotRequest): Observable<Pot> {
    console.log('[MonthApi] updatePot', id, request);
    return this.http.put<Pot>(`/api/pots/${id}`, request);
  }

  /** Drops a Balance Check-in, so that month's balance is calculated again. */
  deleteCheckIn(potId: number, month: string): Observable<void> {
    console.log('[MonthApi] deleteCheckIn', potId, month);
    return this.http.delete<void>(`/api/pots/${potId}/check-ins/${month}`);
  }

  /** Sets the Allocation from `month` onwards; earlier months keep theirs. */
  saveAllocation(month: string, request: AllocationRequest): Observable<unknown> {
    console.log('[MonthApi] saveAllocation', month, request);
    return this.http.put(`/api/months/${month}/allocation`, request);
  }

  /** Records the Pot's actual balance at the end of `month`. */
  checkIn(potId: number, month: string, balance: number): Observable<unknown> {
    console.log('[MonthApi] checkIn', potId, month, balance);
    return this.http.put(`/api/pots/${potId}/check-ins/${month}`, { balance });
  }
}
