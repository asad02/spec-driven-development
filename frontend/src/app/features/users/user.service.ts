import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PageResponse } from '../../core/models/page.model';
import { User, UserQuery, UserRequest } from '../../core/models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/users`;

  list(query: UserQuery): Observable<PageResponse<User>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', query.sort)
      .set('direction', query.direction);

    const term = query.search.trim();
    if (term) {
      params = params.set('search', term);
    }

    return this.http.get<PageResponse<User>>(this.base, { params });
  }

  get(id: string): Observable<User> {
    return this.http.get<User>(`${this.base}/${id}`);
  }

  create(request: UserRequest): Observable<User> {
    return this.http.post<User>(this.base, request);
  }

  update(id: string, request: UserRequest): Observable<User> {
    return this.http.put<User>(`${this.base}/${id}`, request);
  }
}
