import { Injectable } from '@angular/core';

/**
 * Separate storage key from the product UI: the two applications are different
 * origins in development, and sharing a key would make signing out of one look
 * like it signed you out of the other.
 */
const TOKEN_KEY = 'feature-toggle-management.access-token';

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
    try { localStorage.setItem(TOKEN_KEY, token); } catch { /* session-only */ }
  }

  clear(): void {
    this.memoryFallback = null;
    try { localStorage.removeItem(TOKEN_KEY); } catch { /* already gone */ }
  }
}
