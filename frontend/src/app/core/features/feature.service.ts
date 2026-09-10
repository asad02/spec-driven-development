import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { BackendProvider, FeatureState } from './feature.model';

/**
 * Reads and flips the ff4j feature flag that decides which backend nginx proxies
 * /api to. The flag itself lives in PostgreSQL and is evaluated server-side
 * through OpenFeature — this service only reflects and toggles it.
 */
@Injectable({ providedIn: 'root' })
export class FeatureService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/features`;

  /** What the flag says should serve /api. */
  readonly activeBackend = signal<BackendProvider | null>(null);

  /**
   * Which implementation actually answered the last API call, read from the
   * X-Served-By response header. Kept separate from activeBackend on purpose: if
   * these two disagree, routing is broken and the UI should be able to show that.
   */
  readonly servedBy = signal<BackendProvider | null>(null);

  readonly switching = signal(false);

  /** Whether this operator may flip the routing toggle. */
  readonly canToggle = signal(false);

  /** Whether this operator may write user data, or only read it. */
  readonly readOnly = signal(false);

  /** Whether this operator's roles satisfy the user-data-access ACL at all. */
  readonly canAccessData = signal(true);

  /** The roles FF4J_ROLES grants on user-data-access, for the denied message. */
  readonly dataRoles = signal<string[]>([]);

  load(): Observable<FeatureState> {
    return this.http
      .get<FeatureState>(this.baseUrl)
      .pipe(tap((state) => this.apply(state)));
  }

  switchTo(provider: BackendProvider): Observable<FeatureState> {
    this.switching.set(true);
    return this.http.put<FeatureState>(this.baseUrl, { activeBackend: provider }).pipe(
      tap({
        next: (state) => {
          this.apply(state);
          this.switching.set(false);
        },
        error: () => this.switching.set(false),
      }),
    );
  }

  private apply(state: FeatureState): void {
    this.activeBackend.set(state.activeBackend);
    this.canToggle.set(state.canToggle ?? false);
    this.readOnly.set(state.readOnly ?? false);
    this.canAccessData.set(state.canAccessData ?? true);
    this.dataRoles.set(state.dataRoles ?? []);
  }

  /** Drops the previous operator's rights so they cannot flash for the next one. */
  reset(): void {
    this.activeBackend.set(null);
    this.servedBy.set(null);
    this.canToggle.set(false);
    this.readOnly.set(false);
    this.canAccessData.set(true);
    this.dataRoles.set([]);
  }

  recordServedBy(value: string | null): void {
    if (value === 'micronaut' || value === 'springboot') {
      this.servedBy.set(value);
    }
  }
}
