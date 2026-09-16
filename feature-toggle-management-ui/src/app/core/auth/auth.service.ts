import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { TokenStorage } from './token.storage';

interface LoginResponse {
  access_token: string;
  username: string;
  roles: string[];
}

interface JwtClaims { sub?: string; roles?: string[]; exp?: number; }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly storage = inject(TokenStorage);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(this.storage.read());

  private readonly claims = computed<JwtClaims | null>(() => {
    const raw = this.token();
    return raw ? this.decode(raw) : null;
  });

  readonly username = computed(() => this.claims()?.sub ?? null);
  readonly roles = computed<string[]>(() => this.claims()?.roles ?? []);

  /** Full management. Everything else is read-only (technical spec D-7). */
  readonly isAdmin = computed(() => this.roles().includes('ROLE_ADMIN'));
  /** May open the console at all. No other role has access. */
  readonly canRead = computed(() => this.isAdmin() || this.roles().includes('ROLE_VIEWER'));

  readonly isAuthenticated = computed(() => {
    const claims = this.claims();
    return claims !== null && !this.isExpired(claims);
  });

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.authApiBaseUrl}/auth/login`, { username, password })
      .pipe(tap((response) => {
        this.storage.write(response.access_token);
        this.token.set(response.access_token);
      }));
  }

  logout(): void {
    this.clearSession();
    this.router.navigate(['/login'], { replaceUrl: true });
  }

  clearSession(): void {
    this.storage.clear();
    this.token.set(null);
  }

  accessToken(): string | null {
    return this.token();
  }

  private decode(token: string): JwtClaims | null {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    try {
      const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '=');
      return JSON.parse(atob(padded)) as JwtClaims;
    } catch {
      return null;
    }
  }

  private isExpired(claims: JwtClaims): boolean {
    return claims.exp ? claims.exp * 1000 <= Date.now() : false;
  }
}
