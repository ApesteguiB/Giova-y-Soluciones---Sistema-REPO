import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface TenantView {
  id: number;
  publicId: string;
  code: string;
  slug: string;
  legalName: string;
  displayName: string;
  taxId: string | null;
  address: string | null;
  contactEmail: string | null;
  defaultCurrency: 'PEN' | 'USD';
  taxRate: number;
  active: boolean;
  version: number;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
  pdfHeaderText: string | null;
  hasLogo: boolean;
  adminCount: number;
}

export interface TenantInput {
  code: string;
  slug: string;
  legalName: string;
  displayName: string;
  taxId: string | null;
  address: string | null;
  contactEmail: string | null;
  defaultCurrency: 'PEN' | 'USD';
  taxRate: number;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
  pdfHeaderText: string | null;
}

export interface TenantUpdate extends Omit<TenantInput, 'code' | 'slug'> {
  version: number;
}

export interface PlatformAdmin {
  workerId: number;
  username: string;
  displayName: string;
  enabled: boolean;
  version: number;
}

export interface AdminProvisioned {
  admin: PlatformAdmin;
  created: boolean;
  temporaryPassword: string | null;
}

@Injectable({ providedIn: 'root' })
export class PlatformApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/platform';

  listTenants(): Observable<TenantView[]> {
    return this.http.get<TenantView[]>(`${this.base}/tenants`);
  }

  createTenant(input: TenantInput): Observable<TenantView> {
    return this.http.post<TenantView>(`${this.base}/tenants`, input);
  }

  updateTenant(id: number, input: TenantUpdate): Observable<TenantView> {
    return this.http.put<TenantView>(`${this.base}/tenants/${id}`, input);
  }

  setTenantActive(id: number, active: boolean, version: number): Observable<TenantView> {
    return this.http.patch<TenantView>(`${this.base}/tenants/${id}/status`, { active, version });
  }

  uploadLogo(id: number, file: File): Observable<TenantView> {
    const form = new FormData();
    form.append('file', file);
    return this.http.put<TenantView>(`${this.base}/tenants/${id}/logo`, form);
  }

  listAdmins(tenantId: number): Observable<PlatformAdmin[]> {
    return this.http.get<PlatformAdmin[]>(`${this.base}/tenants/${tenantId}/admins`);
  }

  createOrLinkAdmin(
    tenantId: number,
    username: string,
    displayName: string,
  ): Observable<AdminProvisioned> {
    return this.http.post<AdminProvisioned>(`${this.base}/tenants/${tenantId}/admins`, {
      username,
      displayName,
    });
  }

  setAdminEnabled(
    tenantId: number,
    workerId: number,
    enabled: boolean,
    version: number,
  ): Observable<PlatformAdmin> {
    return this.http.patch<PlatformAdmin>(
      `${this.base}/tenants/${tenantId}/admins/${workerId}/status`,
      { enabled, version },
    );
  }

  resetAdminPassword(tenantId: number, workerId: number): Observable<AdminProvisioned> {
    return this.http.post<AdminProvisioned>(
      `${this.base}/tenants/${tenantId}/admins/${workerId}/password-reset`,
      {},
    );
  }
}
