import { Routes } from '@angular/router';

import { currentMonth, MonthPage } from './months/month-page/month-page';

export const routes: Routes = [
  { path: 'months/:month', component: MonthPage },
  { path: '**', redirectTo: () => `/months/${currentMonth()}` },
];
