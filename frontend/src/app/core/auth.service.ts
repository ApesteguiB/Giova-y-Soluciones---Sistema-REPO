import { inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, switchMap, tap } from 'rxjs';
import { CsrfService } from './csrf.service';
import { SessionRoleCode } from './role-navigation';

export interface WorkerSession {
  id: number;
  username: string;
  displayName: string;
  roleCode: SessionRoleCode | null;
  roleLabel: string | null;
  mustChangePassword: boolean;
  membershipSelectionRequired?: boolean;
  memberships?: WorkerMembership[];
}

export interface WorkerMembership {
  membershipId: number;
  tenantId: number;
  tenantCode: string;
  tenantName: string;
  roleCode: SessionRoleCode;
  roleLabel: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  readonly session = signal<WorkerSession | null>(null);

  login(username: string, password: string): Observable<WorkerSession> {
    const body = new HttpParams().set('username', username).set('password', password);
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.post<WorkerSession>('/api/v1/auth/login', body, {
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        }),
      ),
      tap((session) => this.session.set(session)),
    );
  }

  me(): Observable<WorkerSession> {
    return this.http
      .get<WorkerSession>('/api/v1/auth/me')
      .pipe(tap((session) => this.session.set(session)));
  }

  changePassword(currentPassword: string, newPassword: string): Observable<WorkerSession> {
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.post<WorkerSession>('/api/v1/auth/password', { currentPassword, newPassword }),
      ),
      tap((session) => this.session.set(session)),
    );
  }

  selectMembership(membershipId: number): Observable<WorkerSession> {
    return this.csrf.ensureToken().pipe(
      switchMap(() => this.http.post<WorkerSession>('/api/v1/auth/membership', { membershipId })),
      tap((session) => this.session.set(session)),
    );
  }

  logout(): Observable<void> {
    return this.csrf.ensureToken().pipe(
      switchMap(() => this.http.post<void>('/api/v1/auth/logout', {})),
      tap(() => this.session.set(null)),
    );
  }
}
