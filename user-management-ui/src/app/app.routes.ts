import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in · User Management',
    loadComponent: () =>
      import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'users',
    title: 'Users · User Management',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/users/user-list.component').then((m) => m.UserListComponent),
  },
  { path: '', pathMatch: 'full', redirectTo: 'users' },
  { path: '**', redirectTo: 'users' },
];
