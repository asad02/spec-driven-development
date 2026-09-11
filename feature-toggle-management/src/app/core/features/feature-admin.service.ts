import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuditEntry, Feature, FeatureUpsert } from '../models/feature.model';

/**
 * Every call here is also authorised server-side. Hiding a control in the UI is
 * presentation; the server check is the protection (technical spec §5).
 */
@Injectable({ providedIn: 'root' })
export class FeatureAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.adminApiBaseUrl}/features`;

  list(): Observable<Feature[]> {
    return this.http.get<Feature[]>(this.base);
  }

  create(feature: FeatureUpsert): Observable<Feature> {
    return this.http.post<Feature>(this.base, feature);
  }

  update(uid: string, feature: FeatureUpsert): Observable<Feature> {
    return this.http.put<Feature>(`${this.base}/${encodeURIComponent(uid)}`, feature);
  }

  delete(uid: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(uid)}`);
  }

  grantRole(uid: string, role: string): Observable<Feature> {
    return this.http.post<Feature>(`${this.base}/${encodeURIComponent(uid)}/roles/${role}`, {});
  }

  revokeRole(uid: string, role: string): Observable<Feature> {
    return this.http.delete<Feature>(`${this.base}/${encodeURIComponent(uid)}/roles/${role}`);
  }

  setProperty(uid: string, key: string, value: string): Observable<Feature> {
    return this.http.put<Feature>(
      `${this.base}/${encodeURIComponent(uid)}/properties/${encodeURIComponent(key)}`,
      { value },
    );
  }

  audit(limit = 100): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>(`${this.base}/audit`, { params: { limit } });
  }
}
