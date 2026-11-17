import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { CsrfService } from './csrf.service';

export interface ContactRequest {
  requestId: string;
  name: string;
  company: string;
  email: string;
  phone: string;
  subject: string;
  message: string;
  website: string;
}

export interface SubmissionReceipt {
  reference: string;
  registeredAt: string;
}

export interface ClaimRequest {
  requestId: string;
  name: string;
  documentNumber: string;
  email: string;
  phone: string;
  address: string;
  kind: 'RECLAMO' | 'QUEJA';
  goodOrService: string;
  detail: string;
  requestedSolution: string;
  website: string;
}

@Injectable({ providedIn: 'root' })
export class SubmissionApiService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);

  /** El backend exige CSRF: se asegura la cookie antes de enviar el formulario. */
  submitContact(request: ContactRequest): Observable<SubmissionReceipt> {
    return this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.http.post<SubmissionReceipt>('/api/v1/contact-submissions', request)),
      );
  }

  submitClaim(request: ClaimRequest): Observable<SubmissionReceipt> {
    return this.csrf
      .ensureToken()
      .pipe(switchMap(() => this.http.post<SubmissionReceipt>('/api/v1/claims', request)));
  }
}
