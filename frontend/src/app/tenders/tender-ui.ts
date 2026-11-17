import { TenderMatchState, TenderSyncStatus } from '../core/tender-api.service';

export function tenderStateLabel(state: TenderMatchState): string {
  return {
    NEW: 'Nueva',
    REVIEWING: 'En revisión',
    FOLLOWING: 'En seguimiento',
    DISMISSED: 'Descartada',
    CLOSED: 'Cerrada',
  }[state];
}

export function tenderStateClass(state: TenderMatchState): string {
  return {
    NEW: 'new',
    REVIEWING: 'reviewing',
    FOLLOWING: 'following',
    DISMISSED: 'dismissed',
    CLOSED: 'closed',
  }[state];
}

export function syncStatusLabel(status: TenderSyncStatus): string {
  return {
    RUNNING: 'En curso',
    SUCCEEDED: 'Completada',
    FAILED: 'Fallida',
  }[status];
}

export function syncStatusClass(status: TenderSyncStatus): string {
  return status === 'SUCCEEDED' ? 'ok' : status === 'FAILED' ? 'error' : 'running';
}

export function scoreClass(score: number): string {
  if (score >= 80) return 'high';
  if (score >= 50) return 'medium';
  return 'low';
}

export function staleData(lastSuccessfulAt: string | null, now = Date.now()): boolean {
  if (!lastSuccessfulAt) return true;
  const timestamp = Date.parse(lastSuccessfulAt);
  return Number.isNaN(timestamp) || now - timestamp > 24 * 60 * 60 * 1000;
}

export function relativeFreshness(lastSuccessfulAt: string | null, now = Date.now()): string {
  if (!lastSuccessfulAt) return 'Sin sincronización exitosa';
  const timestamp = Date.parse(lastSuccessfulAt);
  if (Number.isNaN(timestamp)) return 'Fecha de actualización no disponible';
  const hours = Math.max(0, Math.floor((now - timestamp) / (60 * 60 * 1000)));
  if (hours < 1) return 'Actualizado hace menos de una hora';
  if (hours === 1) return 'Actualizado hace 1 hora';
  return `Actualizado hace ${hours} horas`;
}
