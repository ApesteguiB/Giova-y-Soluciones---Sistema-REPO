import { HttpClient, HttpEvent, HttpParams, HttpRequest } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { CsrfService } from './csrf.service';

export type CurrencyCode = 'PEN' | 'USD';
export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'CANCELLED';
export type InvoiceType = 'INVOICE' | 'RECEIPT' | 'OTHER';
export type PurchaseOrderStatus = 'REGISTERED' | 'IN_PROGRESS' | 'DELIVERED' | 'CANCELLED';
export type DeliveryStatus = 'DRAFT' | 'SIGNED' | 'CANCELLED';
export type WarrantyStatus = 'ACTIVE' | 'EXPIRED' | 'CLAIMED' | 'CANCELLED';

export interface AccountingItemView {
  lineNumber: number;
  productId: number | null;
  sku: string;
  description: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  lineSubtotal: number;
  lineTax: number;
  lineTotal: number;
}

export interface AccountingItemInput {
  productId: number | null;
  sku: string;
  description: string;
  unit: string;
  quantity: number;
  unitPrice: number;
}

export interface AccountingDocumentView {
  publicId: string;
  role: string;
  originalName: string;
  mediaType: string;
  extension: string;
  sizeBytes: number;
  sha256Hex: string;
  version: number;
  createdAt: string;
  downloadUrl: string;
}

export interface InvoiceView {
  publicId: string;
  invoiceNumber: string;
  invoiceType: InvoiceType;
  customerPublicId: string;
  customerName: string;
  customerDocumentType: string;
  customerDocumentNumber: string;
  quotationPublicId: string | null;
  quotationNumber: string | null;
  quotationOwnerWorkerId: number | null;
  registeredByWorkerId: number;
  registeredByDisplayName: string;
  status: InvoiceStatus;
  issueDate: string;
  dueDate: string | null;
  currencyCode: CurrencyCode;
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
  items: AccountingItemView[];
  documents: AccountingDocumentView[];
}

export interface InvoicePage {
  items: InvoiceView[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface InvoiceInput {
  invoiceNumber: string;
  invoiceType: InvoiceType;
  customerPublicId: string;
  quotationPublicId: string | null;
  issueDate: string;
  dueDate: string | null;
  currencyCode: CurrencyCode;
  exchangeRateToPen: number;
  includesTax: boolean;
  taxRate: number;
  notes: string;
  items: AccountingItemInput[];
}

export interface InvoiceUpdate extends InvoiceInput {
  version: number;
}

/** Una línea leída del XML y lo que se pudo emparejar con el catálogo. */
export interface InvoiceXmlLinePreview {
  lineNumber: number;
  sku: string;
  description: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  matchedProductId: number | null;
  matchedProductName: string | null;
  newProduct: boolean;
}

/** Lo que trae el XML, antes de registrar nada. */
export interface InvoiceXmlPreview {
  invoiceNumber: string;
  invoiceType: InvoiceType;
  issueDate: string;
  dueDate: string | null;
  currencyCode: CurrencyCode;
  supplierName: string | null;
  supplierDocumentNumber: string | null;
  customerName: string | null;
  customerDocumentNumber: string | null;
  customerPublicId: string | null;
  customerFound: boolean;
  duplicate: boolean;
  duplicatePublicId: string | null;
  declaredTotal: number | null;
  calculatedTotal: number;
  newProductCount: number;
  lines: InvoiceXmlLinePreview[];
}

export interface InvoiceXmlImportResult {
  invoice: InvoiceView;
  customerCreated: boolean;
  priceReferencesCreated: number;
  warnings: string[];
}

export interface PurchaseOrderView {
  publicId: string;
  orderNumber: string;
  customerPublicId: string;
  customerName: string;
  customerDocumentType: string;
  customerDocumentNumber: string;
  quotationPublicId: string | null;
  quotationNumber: string | null;
  invoicePublicId: string | null;
  invoiceNumber: string | null;
  responsibleWorkerId: number;
  responsibleWorkerDisplayName: string;
  status: PurchaseOrderStatus;
  issueDate: string;
  expectedDeliveryDate: string | null;
  deliveredDate: string | null;
  currencyCode: CurrencyCode;
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
  items: AccountingItemView[];
  documents: AccountingDocumentView[];
}

export interface PurchaseOrderPage {
  items: PurchaseOrderView[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface PurchaseOrderInput {
  orderNumber: string;
  customerPublicId: string;
  quotationPublicId: string | null;
  invoicePublicId: string | null;
  issueDate: string;
  expectedDeliveryDate: string | null;
  deliveredDate: string | null;
  currencyCode: CurrencyCode;
  exchangeRateToPen: number;
  includesTax: boolean;
  taxRate: number;
  notes: string;
  items: AccountingItemInput[];
}

export interface PurchaseOrderUpdate extends PurchaseOrderInput {
  version: number;
}

export interface DeliveryView {
  publicId: string;
  deliveryNumber: string;
  purchaseOrderPublicId: string;
  orderNumber: string;
  customerPublicId: string;
  customerName: string;
  deliveredAt: string | null;
  receivedByName: string | null;
  receivedByDocument: string | null;
  status: DeliveryStatus;
  notes: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  documents: AccountingDocumentView[];
}

export interface DeliveryPage {
  items: DeliveryView[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface DeliveryInput {
  deliveryNumber: string;
  purchaseOrderPublicId: string;
  deliveredAt: string | null;
  receivedByName: string;
  receivedByDocument: string;
  notes: string;
}

export interface DeliveryUpdate extends DeliveryInput {
  version: number;
}

export interface WarrantyView {
  publicId: string;
  customerPublicId: string;
  customerName: string;
  purchaseOrderPublicId: string | null;
  orderNumber: string | null;
  purchaseOrderLineNumber: number | null;
  productId: number | null;
  productSku: string | null;
  productName: string | null;
  serialNumber: string | null;
  startsOn: string;
  endsOn: string;
  status: WarrantyStatus;
  conditions: string | null;
  notes: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  documents: AccountingDocumentView[];
}

export interface WarrantyPage {
  items: WarrantyView[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface WarrantyInput {
  customerPublicId: string;
  purchaseOrderPublicId: string | null;
  purchaseOrderLineNumber: number | null;
  productId: number | null;
  serialNumber: string;
  startsOn: string;
  endsOn: string;
  conditions: string;
  notes: string;
}

export interface WarrantyUpdate extends WarrantyInput {
  version: number;
}

@Injectable({ providedIn: 'root' })
export class AccountingApiService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);

  private write<T>(request: () => Observable<T>): Observable<T> {
    return this.csrf.ensureToken().pipe(switchMap(() => request()));
  }

  invoices(
    query: {
      q?: string;
      status?: InvoiceStatus;
      type?: InvoiceType;
      customerId?: string;
      quotationId?: string;
      page?: number;
      size?: number;
    } = {},
  ): Observable<InvoicePage> {
    let params = this.params(query);
    if (query.type) params = params.set('type', query.type);
    if (query.quotationId) params = params.set('quotationId', query.quotationId);
    return this.http.get<InvoicePage>('/api/v1/accounting/invoices', { params });
  }

  invoice(publicId: string): Observable<InvoiceView> {
    return this.http.get<InvoiceView>(`/api/v1/accounting/invoices/${publicId}`);
  }

  createInvoice(input: InvoiceInput): Observable<InvoiceView> {
    return this.write(() => this.http.post<InvoiceView>('/api/v1/accounting/invoices', input));
  }

  updateInvoice(publicId: string, input: InvoiceUpdate): Observable<InvoiceView> {
    return this.write(() =>
      this.http.put<InvoiceView>(`/api/v1/accounting/invoices/${publicId}`, input),
    );
  }

  changeInvoiceStatus(
    publicId: string,
    targetStatus: InvoiceStatus,
    version: number,
    reason: string | null = null,
  ): Observable<InvoiceView> {
    return this.write(() =>
      this.http.post<InvoiceView>(`/api/v1/accounting/invoices/${publicId}/status`, {
        targetStatus,
        version,
        reason,
      }),
    );
  }

  uploadInvoiceDocument(
    publicId: string,
    file: File,
    role: string,
    version: number,
  ): Observable<HttpEvent<InvoiceView>> {
    return this.upload(`/api/v1/accounting/invoices/${publicId}/documents`, file, role, version);
  }

  /** Lee el XML y devuelve lo que trae, sin guardar. */
  previewInvoiceXml(file: File, type: InvoiceType = 'INVOICE'): Observable<InvoiceXmlPreview> {
    const body = new FormData();
    body.append('file', this.asXmlFile(file), file.name);
    const params = new HttpParams().set('type', type);
    return this.write(() =>
      this.http.post<InvoiceXmlPreview>('/api/v1/accounting/invoices/import/xml/preview', body, {
        params,
      }),
    );
  }

  /** Registra la factura leída del XML y adjunta el archivo. */
  importInvoiceXml(
    file: File,
    options: {
      type?: InvoiceType;
      registerPrices?: boolean;
      createCustomer?: boolean;
      notes?: string;
    } = {},
  ): Observable<InvoiceXmlImportResult> {
    const body = new FormData();
    body.append('file', this.asXmlFile(file), file.name);
    let params = new HttpParams()
      .set('type', options.type ?? 'INVOICE')
      .set('registerPrices', options.registerPrices ?? true)
      .set('createCustomer', options.createCustomer ?? true);
    if (options.notes?.trim()) params = params.set('notes', options.notes.trim());
    return this.write(() =>
      this.http.post<InvoiceXmlImportResult>('/api/v1/accounting/invoices/import/xml', body, {
        params,
      }),
    );
  }

  /**
   * El backend exige MIME application/xml o text/xml. Algunos navegadores y
   * sistemas operativos reportan los .xml como octet-stream o con el tipo
   * vacío, así que se reenvuelve el archivo con el tipo correcto.
   */
  private asXmlFile(file: File): File {
    const type = (file.type || '').toLowerCase();
    if (type === 'application/xml' || type === 'text/xml') return file;
    return new File([file], file.name, { type: 'text/xml', lastModified: file.lastModified });
  }

  downloadInvoiceDocument(publicId: string, documentPublicId: string): Observable<Blob> {
    return this.http.get(
      `/api/v1/accounting/invoices/${publicId}/documents/${documentPublicId}/content`,
      { responseType: 'blob' },
    );
  }

  purchaseOrders(
    query: {
      q?: string;
      status?: PurchaseOrderStatus;
      customerId?: string;
      quotationId?: string;
      page?: number;
      size?: number;
    } = {},
  ): Observable<PurchaseOrderPage> {
    let params = this.params(query);
    if (query.quotationId) params = params.set('quotationId', query.quotationId);
    return this.http.get<PurchaseOrderPage>('/api/v1/accounting/purchase-orders', { params });
  }

  purchaseOrder(publicId: string): Observable<PurchaseOrderView> {
    return this.http.get<PurchaseOrderView>(`/api/v1/accounting/purchase-orders/${publicId}`);
  }

  createPurchaseOrder(input: PurchaseOrderInput): Observable<PurchaseOrderView> {
    return this.write(() =>
      this.http.post<PurchaseOrderView>('/api/v1/accounting/purchase-orders', input),
    );
  }

  updatePurchaseOrder(publicId: string, input: PurchaseOrderUpdate): Observable<PurchaseOrderView> {
    return this.write(() =>
      this.http.put<PurchaseOrderView>(`/api/v1/accounting/purchase-orders/${publicId}`, input),
    );
  }

  changePurchaseOrderStatus(
    publicId: string,
    targetStatus: PurchaseOrderStatus,
    version: number,
    deliveredDate: string | null = null,
    reason: string | null = null,
  ): Observable<PurchaseOrderView> {
    return this.write(() =>
      this.http.post<PurchaseOrderView>(`/api/v1/accounting/purchase-orders/${publicId}/status`, {
        targetStatus,
        version,
        deliveredDate,
        reason,
      }),
    );
  }

  uploadPurchaseOrderDocument(
    publicId: string,
    file: File,
    role: string,
    version: number,
  ): Observable<HttpEvent<PurchaseOrderView>> {
    return this.upload(
      `/api/v1/accounting/purchase-orders/${publicId}/documents`,
      file,
      role,
      version,
    );
  }

  downloadPurchaseOrderDocument(publicId: string, documentPublicId: string): Observable<Blob> {
    return this.http.get(
      `/api/v1/accounting/purchase-orders/${publicId}/documents/${documentPublicId}/content`,
      { responseType: 'blob' },
    );
  }

  deliveries(
    query: {
      q?: string;
      status?: DeliveryStatus;
      customerId?: string;
      purchaseOrderId?: string;
      page?: number;
      size?: number;
    } = {},
  ): Observable<DeliveryPage> {
    let params = this.params(query);
    if (query.purchaseOrderId) params = params.set('purchaseOrderId', query.purchaseOrderId);
    return this.http.get<DeliveryPage>('/api/v1/accounting/deliveries', { params });
  }

  delivery(publicId: string): Observable<DeliveryView> {
    return this.http.get<DeliveryView>(`/api/v1/accounting/deliveries/${publicId}`);
  }

  createDelivery(input: DeliveryInput): Observable<DeliveryView> {
    return this.write(() => this.http.post<DeliveryView>('/api/v1/accounting/deliveries', input));
  }

  updateDelivery(publicId: string, input: DeliveryUpdate): Observable<DeliveryView> {
    return this.write(() =>
      this.http.put<DeliveryView>(`/api/v1/accounting/deliveries/${publicId}`, input),
    );
  }

  changeDeliveryStatus(
    publicId: string,
    targetStatus: DeliveryStatus,
    version: number,
    deliveredAt: string | null = null,
    reason: string | null = null,
  ): Observable<DeliveryView> {
    return this.write(() =>
      this.http.post<DeliveryView>(`/api/v1/accounting/deliveries/${publicId}/status`, {
        targetStatus,
        version,
        deliveredAt,
        reason,
      }),
    );
  }

  uploadDeliveryDocument(
    publicId: string,
    file: File,
    role: string,
    version: number,
  ): Observable<HttpEvent<DeliveryView>> {
    return this.upload(`/api/v1/accounting/deliveries/${publicId}/documents`, file, role, version);
  }

  downloadDeliveryDocument(publicId: string, documentPublicId: string): Observable<Blob> {
    return this.http.get(
      `/api/v1/accounting/deliveries/${publicId}/documents/${documentPublicId}/content`,
      { responseType: 'blob' },
    );
  }

  warranties(
    query: {
      q?: string;
      status?: WarrantyStatus;
      customerId?: string;
      purchaseOrderId?: string;
      page?: number;
      size?: number;
    } = {},
  ): Observable<WarrantyPage> {
    let params = this.params(query);
    if (query.purchaseOrderId) params = params.set('purchaseOrderId', query.purchaseOrderId);
    return this.http.get<WarrantyPage>('/api/v1/accounting/warranties', { params });
  }

  warranty(publicId: string): Observable<WarrantyView> {
    return this.http.get<WarrantyView>(`/api/v1/accounting/warranties/${publicId}`);
  }

  createWarranty(input: WarrantyInput): Observable<WarrantyView> {
    return this.write(() => this.http.post<WarrantyView>('/api/v1/accounting/warranties', input));
  }

  updateWarranty(publicId: string, input: WarrantyUpdate): Observable<WarrantyView> {
    return this.write(() =>
      this.http.put<WarrantyView>(`/api/v1/accounting/warranties/${publicId}`, input),
    );
  }

  changeWarrantyStatus(
    publicId: string,
    targetStatus: WarrantyStatus,
    version: number,
    reason: string | null = null,
  ): Observable<WarrantyView> {
    return this.write(() =>
      this.http.post<WarrantyView>(`/api/v1/accounting/warranties/${publicId}/status`, {
        targetStatus,
        version,
        reason,
      }),
    );
  }

  uploadWarrantyDocument(
    publicId: string,
    file: File,
    role: string,
    version: number,
  ): Observable<HttpEvent<WarrantyView>> {
    return this.upload(`/api/v1/accounting/warranties/${publicId}/documents`, file, role, version);
  }

  downloadWarrantyDocument(publicId: string, documentPublicId: string): Observable<Blob> {
    return this.http.get(
      `/api/v1/accounting/warranties/${publicId}/documents/${documentPublicId}/content`,
      { responseType: 'blob' },
    );
  }

  private params(query: {
    q?: string;
    status?: string;
    customerId?: string;
    page?: number;
    size?: number;
  }): HttpParams {
    let params = new HttpParams();
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.status) params = params.set('status', query.status);
    if (query.customerId) params = params.set('customerId', query.customerId);
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    return params;
  }

  private upload<T>(
    url: string,
    file: File,
    role: string,
    version: number,
  ): Observable<HttpEvent<T>> {
    const body = new FormData();
    body.append('file', file, file.name);
    const params = new HttpParams().set('role', role).set('version', version);
    return this.csrf
      .ensureToken()
      .pipe(
        switchMap(() =>
          this.http.request<T>(
            new HttpRequest('POST', url, body, { params, reportProgress: true }),
          ),
        ),
      );
  }
}
