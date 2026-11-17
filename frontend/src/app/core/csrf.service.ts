import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class CsrfService {
  private readonly http = inject(HttpClient);
  private readonly token$ = this.http
    .get<{ token: string }>('/api/v1/auth/csrf')
    .pipe(shareReplay(1));

  ensureToken(): Observable<{ token: string }> {
    return this.token$;
  }
}
