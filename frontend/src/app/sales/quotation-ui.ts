import type { OutcomeSource, ProcurementType, QuotationOutcome } from '../core/sales-api.service';

export interface QuotationPreviewLine {
  subtotal: number;
  tax: number;
  total: number;
}

export interface QuotationPreview {
  lines: QuotationPreviewLine[];
  subtotal: number;
  tax: number;
  total: number;
}

export function roundCurrency(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

export function calculateQuotationLinePreview(
  quantity: number,
  unitPrice: number,
  includesTax: boolean,
  taxRate: number,
): QuotationPreviewLine {
  const gross = Math.max(0, Number(quantity) || 0) * Math.max(0, Number(unitPrice) || 0);
  if (includesTax) {
    const total = roundCurrency(gross);
    const subtotal = roundCurrency(total / (1 + Math.max(0, Number(taxRate) || 0)));
    return { subtotal, tax: roundCurrency(total - subtotal), total };
  }

  const subtotal = roundCurrency(gross);
  const tax = roundCurrency(subtotal * Math.max(0, Number(taxRate) || 0));
  return { subtotal, tax, total: roundCurrency(subtotal + tax) };
}

export function calculateQuotationPreview(
  lines: ReadonlyArray<{ quantity: number; unitPrice: number }>,
  includesTax: boolean,
  taxRate: number,
): QuotationPreview {
  const previews = lines.map((line) =>
    calculateQuotationLinePreview(line.quantity, line.unitPrice, includesTax, taxRate),
  );
  return {
    lines: previews,
    subtotal: roundCurrency(previews.reduce((sum, line) => sum + line.subtotal, 0)),
    tax: roundCurrency(previews.reduce((sum, line) => sum + line.tax, 0)),
    total: roundCurrency(previews.reduce((sum, line) => sum + line.total, 0)),
  };
}

// ---------------------------------------------------------------------------
// Procedimiento, plazos y resultado
// ---------------------------------------------------------------------------

export const PROCUREMENT_LABELS: Record<ProcurementType, string> = {
  PERU_COMPRAS: 'Perú Compras (Acuerdo Marco)',
  DIRECTA: 'Contratación directa',
  INDIRECTA: 'Contratación indirecta',
  PRIVADA: 'Empresa privada',
};

export const PROCUREMENT_HINTS: Record<ProcurementType, string> = {
  PERU_COMPRAS:
    'La entidad publica y el plazo se cuenta en días hábiles (de 1 a 3). Si ganas, llega la OCAM.',
  DIRECTA: 'Pedido directo de la entidad. La validez de la oferta la defines tú.',
  INDIRECTA: 'Llega por un intermediario o distribuidor para una entidad.',
  PRIVADA: 'No hay OCAM: la empresa emite su propia orden de compra.',
};

export const OUTCOME_LABELS: Record<QuotationOutcome, string> = {
  PENDIENTE: 'Esperando decisión',
  SELECCIONADA: 'Seleccionada',
  NO_SELECCIONADA: 'No seleccionada',
  DESIERTA: 'Desierta',
  RECHAZADA: 'Rechazada',
  ANULADA: 'Anulada por nosotros',
};

export const OUTCOME_SOURCE_LABELS: Record<OutcomeSource, string> = {
  ENTIDAD: 'Decisión de la entidad',
  NOSOTROS: 'Decisión nuestra',
};

/** Un cierre negativo siempre exige motivo escrito. */
export function outcomeRequiresReason(outcome: QuotationOutcome): boolean {
  return outcome !== 'PENDIENTE' && outcome !== 'SELECCIONADA';
}

/** Solo Perú Compras usa el plazo en días hábiles. */
export function usesBusinessDayDeadline(type: ProcurementType): boolean {
  return type === 'PERU_COMPRAS';
}

/** Una venta a empresa privada no pasa por OCAM. */
export function allowsOcam(type: ProcurementType): boolean {
  return type !== 'PRIVADA';
}

export const MIN_FRAMEWORK_DAYS = 1;
export const MAX_FRAMEWORK_DAYS = 3;

/**
 * Suma días hábiles saltando sábados y domingos.
 *
 * Es la misma regla que aplica el servidor: en Perú Compras el plazo se cuenta
 * en días hábiles, y contarlo corrido hace perder procesos cuando cae fin de
 * semana. Los feriados no se descuentan porque la entidad publica su propia
 * fecha y el vendedor siempre puede corregirla a mano.
 */
export function addBusinessDays(start: Date, businessDays: number): Date {
  const date = new Date(start.getTime());
  let added = 0;
  while (added < businessDays) {
    date.setDate(date.getDate() + 1);
    if (isBusinessDay(date)) added++;
  }
  return date;
}

export function isBusinessDay(date: Date): boolean {
  const day = date.getDay();
  return day !== 0 && day !== 6;
}

/** Días hábiles entre dos fechas ISO (yyyy-MM-dd), sin contar la de inicio. */
export function businessDaysBetween(startIso: string, endIso: string): number {
  const start = parseIsoDate(startIso);
  const end = parseIsoDate(endIso);
  if (!start || !end || end <= start) return 0;
  let count = 0;
  const cursor = new Date(start.getTime());
  while (cursor < end) {
    cursor.setDate(cursor.getDate() + 1);
    if (isBusinessDay(cursor)) count++;
  }
  return count;
}

/** Fecha de hoy más N días hábiles, en formato yyyy-MM-dd para un <input type="date">. */
export function deadlineFromToday(businessDays: number): string {
  return toIsoDate(addBusinessDays(startOfToday(), businessDays));
}

export function startOfToday(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

export function toIsoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Interpreta yyyy-MM-dd como fecha local, no UTC (evita el corrimiento de un día). */
export function parseIsoDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const parts = value.slice(0, 10).split('-');
  if (parts.length !== 3) return null;
  const [year, month, day] = parts.map(Number);
  if (!year || !month || !day) return null;
  return new Date(year, month - 1, day);
}

/**
 * Margen de la línea frente al costo de compra, en porcentaje.
 * Devuelve null cuando no hay costo registrado: mostrar 0 % sería mentir.
 */
export function marginPercent(unitPrice: number, purchasePrice: number | null): number | null {
  const price = Number(unitPrice) || 0;
  const cost = Number(purchasePrice) || 0;
  if (!purchasePrice || cost <= 0 || price <= 0) return null;
  return ((price - cost) / price) * 100;
}

/**
 * Diferencia contra el precio de competencia, en porcentaje.
 * Negativo significa que estamos por debajo (ganando por precio).
 */
export function competitorGapPercent(
  unitPrice: number,
  competitorPrice: number | null,
): number | null {
  const price = Number(unitPrice) || 0;
  const rival = Number(competitorPrice) || 0;
  if (!competitorPrice || rival <= 0 || price <= 0) return null;
  return ((price - rival) / rival) * 100;
}
