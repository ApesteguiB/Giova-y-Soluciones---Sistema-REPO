import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type CompanyWorkerRole = 'ACCOUNTANT' | 'SELLER';

export interface AdminWorker {
  workerId: number;
  username: string;
  displayName: string;
  roleCode: CompanyWorkerRole;
  enabled: boolean;
  version: number;
}

export interface WorkerInput {
  username: string;
  displayName: string;
  roleCode: CompanyWorkerRole;
}

export interface WorkerProvisioned {
  worker: AdminWorker;
  temporaryPassword: string;
}

@Injectable({ providedIn: 'root' })
export class AdminWorkersApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/admin/workers';

  list(): Observable<AdminWorker[]> {
    return this.http.get<AdminWorker[]>(this.baseUrl);
  }

  create(input: WorkerInput): Observable<WorkerProvisioned> {
    return this.http.post<WorkerProvisioned>(this.baseUrl, input);
  }

  update(workerId: number, displayName: string, version: number): Observable<AdminWorker> {
    return this.http.put<AdminWorker>(`${this.baseUrl}/${workerId}`, { displayName, version });
  }

  setEnabled(workerId: number, enabled: boolean, version: number): Observable<AdminWorker> {
    return this.http.patch<AdminWorker>(`${this.baseUrl}/${workerId}/status`, { enabled, version });
  }

  resetPassword(workerId: number): Observable<WorkerProvisioned> {
    return this.http.post<WorkerProvisioned>(`${this.baseUrl}/${workerId}/password-reset`, {});
  }
}
