import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { TokenStorage } from './token.storage';

interface LoginResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  username: string;
  roles: string[];
}

interface JwtClaims {
  sub?: string;
  roles?: string[];
  exp?: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly storage = inject(TokenStorage);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(this.storage.read());

  /** The signed-in operator's username, shown in the toolbar. */
  readonly username = computed(() => this.claims()?.sub ?? null);
  readonly isAuthenticated = computed(() => {
    const claims = this.claims();
    return claims !== null && !this.isExpired(claims);
  });

  private readonly claims = computed<JwtClaims | null>(() => {
    const raw = this.token();
    return raw ? this.decode(raw) : null;
  });

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiBaseUrl}/auth/login`, { username, password })
      .pipe(
        tap((response) => {
          this.storage.write(response.access_token);
          this.token.set(response.access_token);
        }),
      );
  }

  /**
   * Signs the operator out and returns them to the login screen.
   *
   * <p>Discarding the token is only half of it: the guard runs on navigation, not
   * on signal changes, so without this redirect the operator stays on the
   * authenticated page holding no token and every request 401s. replaceUrl keeps
   * the signed-in page out of history, so Back cannot walk into it.
   *
   * <p>Logout is entirely client-side (FR-8) — the server keeps no session.
   */
  logout(): void {
    this.clearSession();
    this.router.navigate(['/login'], { replaceUrl: true });
  }

  /**
   * Discards the token without navigating, for callers that route themselves —
   * the 401 handler needs to add returnUrl and an "expired" reason.
   */
  clearSession(): void {
    this.storage.clear();
    this.token.set(null);
  }

  accessToken(): string | null {
    return this.token();
  }

  private decode(token: string): JwtClaims | null {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }
    try {
      const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '=');
      return JSON.parse(atob(padded)) as JwtClaims;
    } catch {
      // A token we cannot read is a token we cannot trust.
      return null;
    }
  }

  private isExpired(claims: JwtClaims): boolean {
    if (!claims.exp) {
      return false;
    }
    return claims.exp * 1000 <= Date.now();
  }
}
