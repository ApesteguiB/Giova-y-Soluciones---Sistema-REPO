import { AccountingDocumentView, AccountingItemView } from '../core/accounting-api.service';

export type AccountingSection = 'invoices' | 'orders' | 'deliveries' | 'warranties';

export interface AccountingLineDraft {
  productId: number | null;
  sku: string;
  description: string;
  unit: string;
  quantity: number;
  unitPrice: number;
}

interface AccountingSectionMeta {
  title: string;
  description: string;
  singular: string;
  article: string;
  eyebrow: string;
}

export const ACCOUNTING_SECTION_META: Record<AccountingSection, AccountingSectionMeta> = {
  invoices: {
    title: 'Facturas internas',
    description: 'Registra comprobantes y adjunta sus documentos de respaldo.',
    singular: 'factura',
    article: 'Nueva',
    eyebrow: 'Contabilidad',
  },
  orders: {
    title: 'Órdenes de compra',
    description: 'Da seguimiento documental a las órdenes vinculadas a una venta.',
    singular: 'orden',
    article: 'Nueva',
    eyebrow: 'Contabilidad',
  },
  deliveries: {
    title: 'Actas de entrega',
    description: 'Registra la entrega, el receptor y el acta firmada del pedido.',
    singular: 'acta',
    article: 'Nueva',
    eyebrow: 'Contabilidad',
  },
  warranties: {
    title: 'Garantías',
    description: 'Controla la vigencia y los documentos de garantía de cada entrega.',
    singular: 'garantía',
    article: 'Nueva',
    eyebrow: 'Contabilidad',
  },
};

export function statusLabel(status: string): string {
  return (
    {
      DRAFT: 'Borrador',
      ISSUED: 'Emitida',
      CANCELLED: 'Anulada',
      REGISTERED: 'Registrada',
      IN_PROGRESS: 'En proceso',
      DELIVERED: 'Entregada',
      SIGNED: 'Firmada',
      ACTIVE: 'Activa',
      EXPIRED: 'Vencida',
      CLAIMED: 'Reclamada',
    }[status] ?? status
  );
}

export function invoiceTypeLabel(type: string): string {
  return { INVOICE: 'Factura', RECEIPT: 'Boleta', OTHER: 'Otro comprobante' }[type] ?? type;
}

export function documentRoleLabel(role: string): string {
  return (
    {
      PDF: 'PDF',
      XML: 'XML',
      OTHER: 'Otro documento',
      ORDER_PDF: 'Orden PDF',
      CUSTOMER_ATTACHMENT: 'Adjunto del cliente',
      SIGNED_ACT: 'Acta firmada',
      PHOTO_PDF: 'Fotos en PDF',
      OTHER_PDF: 'Otro PDF',
      CERTIFICATE_PDF: 'Certificado',
    }[role] ?? role
  );
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' }).format(date);
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return 'Pendiente';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export function toDateTimeLocal(value: string | null | undefined): string {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

export function toIsoOrNull(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

export function today(): string {
  const date = new Date();
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

export function newLine(): AccountingLineDraft {
  return { productId: null, sku: '', description: '', unit: 'unidad', quantity: 1, unitPrice: 0 };
}

export function lineTotal(item: AccountingItemView): number {
  return Number(item.lineTotal);
}

export function documentSize(document: AccountingDocumentView): string {
  return formatBytes(document.sizeBytes);
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename.replace(/[^a-zA-Z0-9._-]/g, '_');
  anchor.click();
  window.URL.revokeObjectURL(url);
}
