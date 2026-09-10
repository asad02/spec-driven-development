import { Injectable } from '@angular/core';

const TOKEN_KEY = 'user-service.access-token';

/**
 * Holds the access token in localStorage (functional spec OQ-3): it survives a
 * page refresh, at the cost of being readable by any successful XSS. Recorded
 * as security debt — moving to an in-memory token plus a refresh cookie only
 * changes this file.
 *
 * Every access is guarded: private browsing and blocked site data make these
 * APIs throw rather than return null.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  private memoryFallback: string | null = null;

  read(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY) ?? this.memoryFallback;
    } catch {
      return this.memoryFallback;
    }
  }

  write(token: string): void {
    this.memoryFallback = token;
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      // Storage unavailable — the session still works, it just won't survive a reload.
    }
  }

  clear(): void {
    this.memoryFallback = null;
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      // Nothing to do; the in-memory copy is already gone.
    }
  }
}
