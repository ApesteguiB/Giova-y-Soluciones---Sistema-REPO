import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import {
  TenderApiService,
  TenderDetail,
  TenderMatchState,
  TenderMatchView,
  TenderPage,
  TenderSourceStatus,
  TenderSyncRunView,
} from '../core/tender-api.service';
import {
  relativeFreshness,
  scoreClass,
  staleData,
  syncStatusClass,
  syncStatusLabel,
  tenderStateClass,
  tenderStateLabel,
} from './tender-ui';

@Component({
  selector: 'app-tenders-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule],
  templateUrl: './tenders-page.component.html',
  styleUrls: ['../admin/admin-resource-page.css', './tenders-page.component.css'],
})
export class TendersPageComponent implements OnInit {
  private readonly api = inject(TenderApiService);
  private readonly auth = inject(AuthService);

  readonly role = computed(() => this.auth.session()?.roleCode ?? null);
  readonly canSync = computed(() => this.role() === 'ADMIN' || this.role() === 'SELLER');
  readonly canAct = this.canSync;

  readonly matches = signal<TenderMatchView[]>([]);
  readonly sourceStatus = signal<TenderSourceStatus | null>(null);
  readonly detail = signal<TenderDetail | null>(null);
  readonly selectedMatch = signal<TenderMatchView | null>(null);
  readonly syncRun = signal<TenderSyncRunView | null>(null);
  readonly loading = signal(true);
  readonly detailLoading = signal(false);
  readonly syncing = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly busyAction = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalItems = signal(0);
  readonly totalPages = signal(0);
  readonly pageSize = 20;

  query = '';
  stateFilter: 'ALL' | TenderMatchState = 'ALL';
  buyerFilter = '';
  regionFilter = '';
  dateFrom = '';
  dateTo = '';
  minScore: number | null = null;

  readonly tenderStateLabel = tenderStateLabel;
  readonly tenderStateClass = tenderStateClass;
  readonly scoreClass = scoreClass;
  readonly syncStatusLabel = syncStatusLabel;
  readonly syncStatusClass = syncStatusClass;

  ngOnInit(): void {
    this.loadSourceStatus();
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api
      .tenders({
        state: this.stateFilter === 'ALL' ? undefined : this.stateFilter,
        q: this.query,
        buyer: this.buyerFilter,
        region: this.regionFilter,
        from: this.dateFrom || undefined,
        to: this.dateTo || undefined,
        minScore: this.minScore === null ? undefined : this.minScore,
        page: this.page(),
        size: this.pageSize,
      })
      .subscribe({
        next: (page) => this.setPage(page),
        error: (error: HttpErrorResponse) =>
          this.fail(error, 'No se pudieron cargar las licitaciones.'),
      });
  }

  search(): void {
    this.page.set(0);
    this.load();
  }

  previousPage(): void {
    if (this.page() === 0) return;
    this.page.update((value) => value - 1);
    this.load();
  }

  nextPage(): void {
    if (this.page() + 1 >= this.totalPages()) return;
    this.page.update((value) => value + 1);
    this.load();
  }

  openDetail(match: TenderMatchView): void {
    this.error.set('');
    this.notice.set('');
    this.selectedMatch.set(match);
    this.detail.set(null);
    this.detailLoading.set(true);
    this.api.detail(match.process.publicId).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.detailLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.detailLoading.set(false);
        this.selectedMatch.set(null);
        this.error.set(this.apiError(error, 'No se pudo cargar el detalle de la licitación.'));
      },
    });
  }

  closeDetail(): void {
    this.detail.set(null);
    this.selectedMatch.set(null);
  }

  changeMatchState(match: TenderMatchView, targetState: TenderMatchState): void {
    if (!this.canAct() || this.busyAction()) return;
    const targetLabel = tenderStateLabel(targetState).toLowerCase();
    if (targetState === 'DISMISSED' || targetState === 'CLOSED') {
      if (!window.confirm(`¿Marcar la oportunidad como ${targetLabel}?`)) return;
    }
    this.error.set('');
    this.notice.set('');
    this.busyAction.set(this.actionKey(match, targetState));
    this.api
      .changeMatchState(match.process.publicId, {
        profilePublicId: match.profilePublicId,
        targetState,
        note: '',
        version: match.version,
      })
      .subscribe({
        next: (updated) => {
          this.busyAction.set(null);
          this.replaceMatch(updated);
          this.notice.set(`Oportunidad marcada como ${targetLabel}.`);
          this.load();
        },
        error: (error: HttpErrorResponse) => {
          this.busyAction.set(null);
          this.error.set(
            this.apiError(error, 'No se pudo actualizar el estado de la oportunidad.'),
          );
        },
      });
  }

  startSync(): void {
    if (!this.canSync() || this.syncing()) return;
    if (!this.sourceStatus()?.integrationEnabled) {
      this.error.set('La sincronización de OECE está deshabilitada en este entorno.');
      return;
    }
    this.error.set('');
    this.notice.set('');
    this.syncing.set(true);
    this.syncRun.set(null);
    this.api.sync().subscribe({
      next: (accepted) => {
        this.notice.set(`Sincronización aceptada. Ejecución ${accepted.runId}.`);
        this.pollSync(accepted.runId, 0);
      },
      error: (error: HttpErrorResponse) => {
        this.syncing.set(false);
        this.error.set(this.apiError(error, 'No se pudo iniciar la sincronización.'));
        this.loadSourceStatus();
      },
    });
  }

  isStale(): boolean {
    return staleData(this.sourceStatus()?.lastSuccessfulAt ?? null);
  }

  freshness(): string {
    return relativeFreshness(this.sourceStatus()?.lastSuccessfulAt ?? null);
  }

  actionLabel(state: TenderMatchState): string | null {
    return state === 'NEW'
      ? 'Revisar'
      : state === 'REVIEWING'
        ? 'Seguir'
        : state === 'FOLLOWING' || state === 'DISMISSED'
          ? 'Cerrar'
          : null;
  }

  actionTarget(state: TenderMatchState): TenderMatchState | null {
    return state === 'NEW'
      ? 'REVIEWING'
      : state === 'REVIEWING'
        ? 'FOLLOWING'
        : state === 'FOLLOWING' || state === 'DISMISSED'
          ? 'CLOSED'
          : null;
  }

  canDismiss(state: TenderMatchState): boolean {
    return state === 'REVIEWING';
  }

  isBusy(match: TenderMatchView, targetState: TenderMatchState): boolean {
    return this.busyAction() === this.actionKey(match, targetState);
  }

  private actionKey(match: TenderMatchView, targetState: TenderMatchState): string {
    return `${match.publicId}:${targetState}`;
  }

  private loadSourceStatus(): void {
    this.api.sourceStatus().subscribe({
      next: (status) => this.sourceStatus.set(status),
      error: (error: HttpErrorResponse) => {
        this.error.set(this.apiError(error, 'No se pudo consultar el estado de la fuente.'));
      },
    });
  }

  private pollSync(runId: string, attempt: number): void {
    this.api.syncRun(runId).subscribe({
      next: (run) => {
        this.syncRun.set(run);
        if (run.status === 'RUNNING' && attempt < 20) {
          window.setTimeout(() => this.pollSync(runId, attempt + 1), 1500);
          return;
        }
        this.syncing.set(false);
        this.loadSourceStatus();
        if (run.status === 'SUCCEEDED') {
          this.notice.set(
            `Sincronización completada: ${run.releases} releases y ${run.matches} coincidencias.`,
          );
          this.load();
        } else if (run.status === 'FAILED') {
          this.error.set(
            run.errorMessage || 'La sincronización terminó con error. Se conserva el histórico.',
          );
        } else {
          this.notice.set(
            'La sincronización sigue en curso. Puedes actualizar el estado más tarde.',
          );
        }
      },
      error: (error: HttpErrorResponse) => {
        this.syncing.set(false);
        this.error.set(
          this.apiError(error, 'No se pudo consultar la ejecución de sincronización.'),
        );
        this.loadSourceStatus();
      },
    });
  }

  private replaceMatch(updated: TenderMatchView): void {
    this.matches.update((items) =>
      items.map((item) => (item.publicId === updated.publicId ? updated : item)),
    );
    const currentDetail = this.detail();
    if (currentDetail) {
      this.detail.set({
        ...currentDetail,
        matches: currentDetail.matches.map((item) =>
          item.publicId === updated.publicId ? updated : item,
        ),
      });
    }
  }

  private setPage(page: TenderPage): void {
    this.matches.set(page.items);
    this.totalItems.set(page.totalItems);
    this.totalPages.set(page.totalPages);
    this.loading.set(false);
  }

  private fail(error: HttpErrorResponse, fallback: string): void {
    this.loading.set(false);
    this.error.set(this.apiError(error, fallback));
  }

  private apiError(error: HttpErrorResponse, fallback: string): string {
    const body = error.error as { detail?: string; message?: string } | null;
    return body?.detail || body?.message || fallback;
  }
}
