import { Routes } from '@angular/router';

import { consoleGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in · Feature Toggle Management',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'features',
    title: 'Feature toggles · Feature Toggle Management',
    canActivate: [consoleGuard],
    loadComponent: () => import('./features/list/feature-list.component').then((m) => m.FeatureListComponent),
  },
  {
    path: 'audit',
    title: 'Change history · Feature Toggle Management',
    canActivate: [consoleGuard],
    loadComponent: () => import('./features/audit/audit-log.component').then((m) => m.AuditLogComponent),
  },
  { path: '', pathMatch: 'full', redirectTo: 'features' },
  { path: '**', redirectTo: 'features' },
];
