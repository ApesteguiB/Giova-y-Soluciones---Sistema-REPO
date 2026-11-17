import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { CsrfService } from './csrf.service';

export interface CustomerView {
  publicId: string;
  ownerWorkerId: number;
  ownerDisplayName: string;
  customerType: 'PERSON' | 'COMPANY';
  documentType: 'DNI' | 'RUC' | 'CE' | 'OTHER';
  documentNumber: string;
  businessName: string | null;
  contactName: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
  notes: string | null;
  active: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerPage {
  items: CustomerView[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface CustomerInput {
  customerType: 'PERSON' | 'COMPANY';
  documentType: 'DNI' | 'RUC' | 'CE' | 'OTHER';
  documentNumber: string;
  businessName: string;
  contactName: string;
  email: string;
  phone: string;
  address: string;
  notes: string;
}

export interface PriceReferenceView {
  id: number;
  productId: number;
  productSku: string;
  productName: string;
  recordedByWorkerId: number;
  recordedByDisplayName: string;
  supplierName: string | null;
  sourceReference: string | null;
  currencyCode: 'PEN' | 'USD';
  unitPrice: number;
  includesTax: boolean;
  taxRate: number | null;
  exchangeRateToPen: number | null;
  effectiveAt: string;
  notes: string | null;
  createdAt: string;
}

export interface PriceReferencePage {
  items: PriceReferenceView[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface PriceReferenceInput {
  productId: number;
  supplierName: string;
  sourceReference: string;
  currencyCode: 'PEN' | 'USD';
  unitPrice: number;
  includesTax: boolean;
  taxRate: number | null;
  exchangeRateToPen: number | null;
  effectiveAt: string;
  notes: string;
}

export type QuotationStatus =
  'DRAFT' | 'ISSUED' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED';

/** Cómo llegó la oportunidad. Define el plazo, la OCAM y los resultados posibles. */
export type ProcurementType = 'PERU_COMPRAS' | 'DIRECTA' | 'INDIRECTA' | 'PRIVADA';

/** El resultado real del proceso, separado del estado del documento. */
export type QuotationOutcome =
  'PENDIENTE' | 'SELECCIONADA' | 'NO_SELECCIONADA' | 'DESIERTA' | 'RECHAZADA' | 'ANULADA';

/** Quién cerró el proceso: la entidad o nosotros. */
export type OutcomeSource = 'ENTIDAD' | 'NOSOTROS';

export interface QuotationItemView {
  lineNumber: number;
  productId: number | null;
  sku: string;
  description: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  competitorPrice: number | null;
  purchasePrice: number | null;
  lineSubtotal: number;
  lineTax: number;
  lineTotal: number;
}

export interface QuotationView {
  publicId: string;
  quotationNumber: string | null;
  customerPublicId: string;
  customerName: string;
  customerDocumentType: string;
  customerDocumentNumber: string;
  ownerWorkerId: number;
  ownerDisplayName: string;
  status: QuotationStatus;
  procurementType: ProcurementType;
  outcome: QuotationOutcome | null;
  outcomeReason: string | null;
  outcomeSource: OutcomeSource | null;
  outcomeAt: string | null;
  issuedAt: string | null;
  validUntil: string | null;
  deliveryTermDays: number | null;
  warrantyMonths: number | null;
  ocamNumber: string | null;
  ocamDate: string | null;
  currencyCode: 'PEN' | 'USD';
  exchangeRateToPen: number;
  includesTax: boolean;
  taxRate: number;
  subtotalAmount: number;
  taxAmount: number;
  totalAmount: number;
  notes: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  issuedPdfPublicId: string | null;
  items: QuotationItemView[];
}

export interface QuotationPage {
  items: QuotationView[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

// ---------------------------------------------------------------------------
// Historial comercial de un producto
//
// Lo que el vendedor necesita saber justo cuando está poniendo el precio:
// a cuánto lo vendimos antes, con qué precio nos ganaron cuando lo perdimos,
// y cuánto nos costó la última vez.
// ---------------------------------------------------------------------------

export interface WonSale {
  quotationPublicId: string;
  quotationNumber: string | null;
  customerName: string;
  date: string | null;
  currencyCode: 'PEN' | 'USD';
  unitPrice: number;
  quantity: number;
  competitorPrice: number | null;
  procurementType: ProcurementType;
  invoiced: boolean;
}

export interface LostSale {
  quotationPublicId: string;
  quotationNumber: string | null;
  customerName: string;
  date: string | null;
  currencyCode: 'PEN' | 'USD';
  ourPrice: number;
  competitorPrice: number;
  outcome: QuotationOutcome;
  reason: string | null;
  procurementType: ProcurementType;
}

export interface LastPurchase {
  supplierName: string | null;
  sourceReference: string | null;
  currencyCode: 'PEN' | 'USD';
  unitPrice: number;
  includesTax: boolean;
  exchangeRateToPen: number | null;
  date: string | null;
}

export interface PriceStats {
  timesQuoted: number;
  timesWon: number;
  winRate: number | null;
  bestWonPrice: number | null;
  lowestCompetitor: number | null;
  suggestedPrice: number | null;
  suggestionBasis: string;
}

export interface ProductPriceHistory {
  productId: number;
  sku: string;
  name: string;
  lastPurchase: LastPurchase | null;
  wonSales: WonSale[];
  lostSales: LostSale[];
  stats: PriceStats;
}

export interface QuotationItemInput {
  productId: number | null;
  sku: string;
  description: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  /** Precio con el que compite esta línea. Opcional: no siempre se conoce. */
  competitorPrice: number | null;
  /** Costo de compra usado para armar el precio. Opcional. */
  purchasePrice: number | null;
}

export interface QuotationInput {
  customerPublicId: string;
  procurementType: ProcurementType;
  currencyCode: 'PEN' | 'USD';
  exchangeRateToPen: number;
  includesTax: boolean;
  taxRate: number;
  validUntil: string | null;
  deliveryTermDays: number | null;
  warrantyMonths: number | null;
  notes: string | null;
  items: QuotationItemInput[];
}

export interface QuotationUpdate extends QuotationInput {
  version: number;
}

@Injectable({ providedIn: 'root' })
export class SalesApiService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);

  private write<T>(request: () => Observable<T>): Observable<T> {
    return this.csrf.ensureToken().pipe(switchMap(() => request()));
  }

  customers(
    query: {
      q?: string;
      active?: boolean;
      owner?: 'all' | 'me';
      page?: number;
      size?: number;
    } = {},
  ) {
    let params = new HttpParams();
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.active !== undefined) params = params.set('active', query.active);
    if (query.owner) params = params.set('owner', query.owner);
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    return this.http.get<CustomerPage>('/api/v1/sales/customers', { params });
  }

  customer(publicId: string) {
    return this.http.get<CustomerView>(`/api/v1/sales/customers/${publicId}`);
  }

  createCustomer(input: CustomerInput) {
    return this.write(() => this.http.post<CustomerView>('/api/v1/sales/customers', input));
  }

  updateCustomer(publicId: string, input: CustomerInput & { version: number }) {
    return this.write(() =>
      this.http.put<CustomerView>(`/api/v1/sales/customers/${publicId}`, input),
    );
  }

  setCustomerStatus(item: CustomerView, active: boolean) {
    return this.write(() =>
      this.http.patch<CustomerView>(`/api/v1/sales/customers/${item.publicId}/status`, {
        active,
        version: item.version,
      }),
    );
  }

  priceReferences(
    query: { productId?: number; from?: string; to?: string; page?: number; size?: number } = {},
  ) {
    let params = new HttpParams();
    if (query.productId) params = params.set('productId', query.productId);
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    return this.http.get<PriceReferencePage>('/api/v1/sales/price-references', { params });
  }

  priceReference(id: number) {
    return this.http.get<PriceReferenceView>(`/api/v1/sales/price-references/${id}`);
  }

  createPriceReference(input: PriceReferenceInput) {
    return this.write(() =>
      this.http.post<PriceReferenceView>('/api/v1/sales/price-references', input),
    );
  }

  updatePriceReference(id: number, input: PriceReferenceInput) {
    return this.write(() =>
      this.http.put<PriceReferenceView>(`/api/v1/sales/price-references/${id}`, input),
    );
  }

  quotations(
    query: {
      q?: string;
      status?: QuotationStatus;
      procurementType?: ProcurementType;
      outcome?: QuotationOutcome;
      customerId?: string;
      owner?: 'all' | 'me';
      from?: string;
      to?: string;
      page?: number;
      size?: number;
    } = {},
  ) {
    let params = new HttpParams();
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.status) params = params.set('status', query.status);
    if (query.procurementType) params = params.set('procurementType', query.procurementType);
    if (query.outcome) params = params.set('outcome', query.outcome);
    if (query.customerId) params = params.set('customerId', query.customerId);
    if (query.owner) params = params.set('owner', query.owner);
    if (query.from) params = params.set('from', query.from);
    if (query.to) params = params.set('to', query.to);
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    return this.http.get<QuotationPage>('/api/v1/sales/quotations', { params });
  }

  quotation(publicId: string) {
    return this.http.get<QuotationView>(`/api/v1/sales/quotations/${publicId}`);
  }

  createQuotation(input: QuotationInput) {
    return this.write(() => this.http.post<QuotationView>('/api/v1/sales/quotations', input));
  }

  updateQuotation(publicId: string, input: QuotationUpdate) {
    return this.write(() =>
      this.http.put<QuotationView>(`/api/v1/sales/quotations/${publicId}`, input),
    );
  }

  issueQuotation(publicId: string, version: number) {
    return this.write(() =>
      this.http.post<QuotationView>(`/api/v1/sales/quotations/${publicId}/issue`, { version }),
    );
  }

  changeQuotationStatus(
    publicId: string,
    targetStatus: QuotationStatus,
    version: number,
    reason: string | null = null,
  ) {
    return this.write(() =>
      this.http.post<QuotationView>(`/api/v1/sales/quotations/${publicId}/status`, {
        targetStatus,
        version,
        reason,
      }),
    );
  }

  /**
   * Cierra el proceso con su resultado real. El servidor exige motivo cuando el
   * resultado es negativo, así que el formulario no deja enviarlo vacío.
   */
  setQuotationOutcome(
    publicId: string,
    outcome: QuotationOutcome,
    version: number,
    reason: string | null = null,
    source: OutcomeSource | null = null,
  ) {
    return this.write(() =>
      this.http.post<QuotationView>(`/api/v1/sales/quotations/${publicId}/outcome`, {
        outcome,
        reason,
        source,
        version,
      }),
    );
  }

  /** Registra la OCAM, que suele llegar días después de ganar. */
  setQuotationOcam(
    publicId: string,
    version: number,
    ocamNumber: string | null,
    ocamDate: string | null,
  ) {
    return this.write(() =>
      this.http.post<QuotationView>(`/api/v1/sales/quotations/${publicId}/ocam`, {
        ocamNumber,
        ocamDate,
        version,
      }),
    );
  }

  /** Historial comercial del producto, para mostrarlo al cotizar. */
  priceHistory(productId: number) {
    return this.http.get<ProductPriceHistory>(`/api/v1/sales/price-history/products/${productId}`);
  }

  /** Igual, pero para líneas escritas a mano donde solo se tiene el código. */
  priceHistoryBySku(sku: string) {
    return this.http.get<ProductPriceHistory>('/api/v1/sales/price-history/by-sku', {
      params: new HttpParams().set('sku', sku),
    });
  }

  downloadQuotationPdf(publicId: string) {
    return this.http.get(`/api/v1/sales/quotations/${publicId}/pdf`, {
      responseType: 'blob',
    });
  }
}
