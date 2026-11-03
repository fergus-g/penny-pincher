import { Routes } from '@angular/router';

import { currentMonth, MonthPage } from './months/month-page/month-page';
import { TrendsPage } from './trends/trends-page';

export const routes: Routes = [
  { path: 'months/:month', component: MonthPage },
  { path: 'trends', component: TrendsPage },
  { path: '**', redirectTo: () => `/months/${currentMonth()}` },
];
