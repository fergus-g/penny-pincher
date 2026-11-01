import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { MonthSummary } from './month.model';

@Service()
export class MonthApi {
  private readonly http = inject(HttpClient);

  getMonth(month: string): Observable<MonthSummary> {
    console.log('[MonthApi] getMonth', month);
    return this.http.get<MonthSummary>(`/api/months/${month}`);
  }
}
