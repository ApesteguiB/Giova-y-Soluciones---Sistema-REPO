import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { CsrfService } from './csrf.service';

export type TenderMatchState = 'NEW' | 'REVIEWING' | 'FOLLOWING' | 'DISMISSED' | 'CLOSED';
export type TenderSyncStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type TenderSyncTrigger = 'SCHEDULED' | 'MANUAL';

export interface TenderProcessView {
  publicId: string;
  ocid: string;
  title: string;
  description: string | null;
  buyerId: string | null;
  buyerName: string | null;
  region: string | null;
  classificationCodes: string[];
  procurementMethod: string | null;
  status: string | null;
  currencyCode: string | null;
  amount: number | null;
  tenderStart: string | null;
  tenderEnd: string | null;
  officialUrl: string | null;
  latestReleaseDate: string | null;
  sourceHash: string;
}

export interface TenderMatchView {
  publicId: string;
  process: TenderProcessView;
  profilePublicId: string;
  profileName: string;
  ownerWorkerId: number | null;
  score: number;
  reasons: string[];
  state: TenderMatchState;
  matchedAt: string;
  version: number;
}

export interface TenderReleaseView {
  publicId: string;
  releaseId: string;
  ocid: string;
  publishedDate: string;
  releaseTag: string | null;
  sourceUrl: string | null;
  sourceHash: string;
  firstSeenAt: string;
  lastSeenAt: string;
}

export interface TenderPage {
  items: TenderMatchView[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface TenderDetail {
  process: TenderProcessView;
  releases: TenderReleaseView[];
  matches: TenderMatchView[];
}

export interface TenderSourceStatus {
  code: string;
  baseUrl: string;
  configuredEnabled: boolean;
  integrationEnabled: boolean;
  lastSuccessfulAt: string | null;
  lastRun: TenderSyncRunView | null;
}

export interface TenderSyncAccepted {
  runId: string;
  status: TenderSyncStatus;
}

export interface TenderSyncRunView {
  runId: string;
  sourceCode: string;
  trigger: TenderSyncTrigger;
  requestedBy: number | null;
  startedAt: string;
  finishedAt: string | null;
  status: TenderSyncStatus;
  cursorBefore: string | null;
  cursorAfter: string | null;
  pages: number;
  releases: number;
  upserts: number;
  matches: number;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface TenderProfileView {
  publicId: string;
  name: string;
  active: boolean;
  version: number;
}

@Injectable({ providedIn: 'root' })
export class TenderApiService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);

  private write<T>(request: () => Observable<T>): Observable<T> {
    return this.csrf.ensureToken().pipe(switchMap(() => request()));
  }

  sourceStatus(): Observable<TenderSourceStatus> {
    return this.http.get<TenderSourceStatus>('/api/v1/tenders/source-status');
  }

  tenders(
    query: {
      state?: TenderMatchState;
      q?: string;
      buyer?: string;
      region?: string;
      from?: string;
      to?: string;
      minScore?: number;
      page?: number;
      size?: number;
    } = {},
  ): Observable<TenderPage> {
    let params = new HttpParams();
    if (query.state) params = params.set('state', query.state);
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.buyer?.trim()) params = params.set('buyer', query.buyer.trim());
    if (query.region?.trim()) params = params.set('region', query.region.trim());
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    if (query.minScore !== undefined) params = params.set('minScore', query.minScore);
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    return this.http.get<TenderPage>('/api/v1/tenders', { params });
  }

  detail(publicId: string): Observable<TenderDetail> {
    return this.http.get<TenderDetail>(`/api/v1/tenders/${publicId}`);
  }

  sync(): Observable<TenderSyncAccepted> {
    return this.write(() => this.http.post<TenderSyncAccepted>('/api/v1/tenders/sync', {}));
  }

  syncRun(runId: string): Observable<TenderSyncRunView> {
    return this.http.get<TenderSyncRunView>(`/api/v1/tenders/sync-runs/${runId}`);
  }

  profiles(): Observable<TenderProfileView[]> {
    return this.http.get<TenderProfileView[]>('/api/v1/tenders/match-profiles');
  }

  changeMatchState(
    processPublicId: string,
    input: {
      profilePublicId: string;
      targetState: TenderMatchState;
      note: string;
      version: number;
    },
  ): Observable<TenderMatchView> {
    return this.write(() =>
      this.http.post<TenderMatchView>(`/api/v1/tenders/${processPublicId}/match-state`, input),
    );
  }
}
