import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { CreateEntryRequest, Entry, MonthSummary, UpdateEntryRequest } from './month.model';

@Service()
export class MonthApi {
  private readonly http = inject(HttpClient);

  getMonth(month: string): Observable<MonthSummary> {
    console.log('[MonthApi] getMonth', month);
    return this.http.get<MonthSummary>(`/api/months/${month}`);
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
}
