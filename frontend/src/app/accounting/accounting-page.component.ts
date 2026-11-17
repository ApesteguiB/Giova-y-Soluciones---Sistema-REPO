import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal, WritableSignal } from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { CatalogApiService, Product } from '../core/catalog-api.service';
import {
  AccountingApiService,
  AccountingDocumentView,
  AccountingItemInput,
  AccountingItemView,
  CurrencyCode,
  DeliveryInput,
  DeliveryStatus,
  DeliveryUpdate,
  DeliveryView,
  InvoiceInput,
  InvoiceStatus,
  InvoiceType,
  InvoiceUpdate,
  InvoiceView,
  InvoiceXmlImportResult,
  InvoiceXmlPreview,
  PurchaseOrderInput,
  PurchaseOrderStatus,
  PurchaseOrderUpdate,
  PurchaseOrderView,
  WarrantyInput,
  WarrantyStatus,
  WarrantyUpdate,
  WarrantyView,
} from '../core/accounting-api.service';
import { CustomerView, QuotationView, SalesApiService } from '../core/sales-api.service';
import {
  ACCOUNTING_SECTION_META,
  AccountingLineDraft,
  AccountingSection,
  documentRoleLabel,
  documentSize,
  downloadBlob,
  formatBytes,
  formatDate,
  formatDateTime,
  invoiceTypeLabel,
  newLine,
  statusLabel,
  today,
  toDateTimeLocal,
  toIsoOrNull,
} from './accounting-ui';

interface ProductOption {
  id: number;
  sku: string;
  name: string;
}

type AccountingRecord = InvoiceView | PurchaseOrderView | DeliveryView | WarrantyView;

interface StatusAction {
  value: string;
  label: string;
}

interface SectionLink {
  key: AccountingSection;
  label: string;
  path: string;
}

@Component({
  selector: 'app-accounting-page',
  standalone: true,
  imports: [DecimalPipe, FormsModule, NgTemplateOutlet, ReactiveFormsModule, RouterLink],
  templateUrl: './accounting-page.component.html',
  styleUrls: ['../admin/admin-resource-page.css', './accounting-page.component.css'],
})
export class AccountingPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(AccountingApiService);
  private readonly sales = inject(SalesApiService);
  private readonly catalog = inject(CatalogApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly section = signal<AccountingSection>('invoices');
  readonly meta = computed(() => ACCOUNTING_SECTION_META[this.section()]);
  readonly role = computed(() => this.auth.session()?.roleCode ?? null);
  readonly canWrite = computed(() => this.role() === 'ADMIN' || this.role() === 'ACCOUNTANT');
  readonly isSeller = computed(() => this.role() === 'SELLER');

  readonly sectionLinks: readonly SectionLink[] = [
    { key: 'invoices', label: 'Facturas', path: '/operaciones/contabilidad/facturas' },
    { key: 'orders', label: 'Órdenes', path: '/operaciones/contabilidad/ordenes' },
    { key: 'deliveries', label: 'Actas', path: '/operaciones/contabilidad/entregas' },
    { key: 'warranties', label: 'Garantías', path: '/operaciones/contabilidad/garantias' },
  ];

  readonly invoices = signal<InvoiceView[]>([]);
  readonly orders = signal<PurchaseOrderView[]>([]);
  readonly deliveries = signal<DeliveryView[]>([]);
  readonly warranties = signal<WarrantyView[]>([]);
  readonly customers = signal<CustomerView[]>([]);
  readonly quotations = signal<QuotationView[]>([]);
  readonly invoiceOptions = signal<InvoiceView[]>([]);
  readonly orderOptions = signal<PurchaseOrderView[]>([]);
  readonly products = signal<ProductOption[]>([]);
  readonly loading = signal(true);
  readonly detailLoading = signal(false);
  readonly saving = signal(false);
  readonly uploading = signal(false);
  readonly uploadProgress = signal(0);
  readonly error = signal('');
  readonly notice = signal('');
  readonly formOpen = signal(false);
  readonly detail = signal<AccountingRecord | null>(null);
  readonly editing = signal<AccountingRecord | null>(null);
  readonly selectedFile = signal<File | null>(null);
  readonly uploadError = signal('');
  readonly busyAction = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalItems = signal(0);
  readonly totalPages = signal(0);
  readonly pageSize = 20;

  query = '';
  statusFilter = 'ALL';
  customerFilter = '';
  uploadRole = '';

  readonly invoiceForm = this.fb.nonNullable.group({
    invoiceNumber: ['', [Validators.required, Validators.maxLength(40)]],
    invoiceType: ['INVOICE' as InvoiceType, Validators.required],
    customerPublicId: ['', Validators.required],
    quotationPublicId: [''],
    issueDate: [today(), Validators.required],
    dueDate: [''],
    currencyCode: ['PEN' as CurrencyCode, Validators.required],
    exchangeRateToPen: [1, [Validators.required, Validators.min(0.000001)]],
    includesTax: [false],
    taxRate: [0.18, [Validators.required, Validators.min(0), Validators.max(1)]],
    notes: ['', Validators.maxLength(1500)],
  });
  readonly invoiceLines = this.fb.array([this.createLine()]);

  readonly orderForm = this.fb.nonNullable.group({
    orderNumber: ['', [Validators.required, Validators.maxLength(40)]],
    customerPublicId: ['', Validators.required],
    quotationPublicId: [''],
    invoicePublicId: [''],
    issueDate: [today(), Validators.required],
    expectedDeliveryDate: [''],
    deliveredDate: [''],
    currencyCode: ['PEN' as CurrencyCode, Validators.required],
    exchangeRateToPen: [1, [Validators.required, Validators.min(0.000001)]],
    includesTax: [false],
    taxRate: [0.18, [Validators.required, Validators.min(0), Validators.max(1)]],
    notes: ['', Validators.maxLength(1500)],
  });
  readonly orderLines = this.fb.array([this.createLine()]);

  readonly deliveryForm = this.fb.nonNullable.group({
    deliveryNumber: ['', [Validators.required, Validators.maxLength(40)]],
    purchaseOrderPublicId: ['', Validators.required],
    deliveredAt: [''],
    receivedByName: ['', Validators.maxLength(160)],
    receivedByDocument: ['', Validators.maxLength(40)],
    notes: ['', Validators.maxLength(1500)],
  });

  readonly warrantyForm = this.fb.group({
    customerPublicId: ['', [Validators.required]],
    purchaseOrderPublicId: [''],
    purchaseOrderLineNumber: [null as number | null],
    productId: [null as number | null],
    serialNumber: ['', Validators.maxLength(120)],
    startsOn: [today(), Validators.required],
    endsOn: [today(), Validators.required],
    conditions: ['', Validators.maxLength(1500)],
    notes: ['', Validators.maxLength(1500)],
  });

  readonly formatDate = formatDate;
  readonly formatDateTime = formatDateTime;
  readonly statusLabel = statusLabel;
  readonly invoiceTypeLabel = invoiceTypeLabel;
  readonly documentRoleLabel = documentRoleLabel;
  readonly documentSize = documentSize;
  readonly formatBytes = formatBytes;

  ngOnInit(): void {
    const configuredSection = this.route.snapshot.data['accountingSection'] as AccountingSection;
    if (configuredSection && ACCOUNTING_SECTION_META[configuredSection]) {
      this.section.set(configuredSection);
    }
    this.loadLookups();
    this.load();
  }

  get activeItems(): AccountingRecord[] {
    switch (this.section()) {
      case 'invoices':
        return this.invoices();
      case 'orders':
        return this.orders();
      case 'deliveries':
        return this.deliveries();
      case 'warranties':
        return this.warranties();
    }
  }

  private createLine(value: Partial<AccountingLineDraft> = {}) {
    const line = { ...newLine(), ...value };
    return this.fb.group({
      productId: [line.productId],
      sku: [line.sku, [Validators.required, Validators.maxLength(80)]],
      description: [line.description, [Validators.required, Validators.maxLength(500)]],
      unit: [line.unit || 'unidad', [Validators.required, Validators.maxLength(30)]],
      quantity: [line.quantity, [Validators.required, Validators.min(0.0001)]],
      unitPrice: [line.unitPrice, [Validators.required, Validators.min(0)]],
    });
  }

  loadLookups(): void {
    const customerOwner = this.role() === 'SELLER' ? 'me' : 'all';
    this.sales.customers({ active: true, owner: customerOwner, page: 0, size: 100 }).subscribe({
      next: (page) => this.customers.set(page.items),
      error: () => this.error.set('No se pudieron cargar los clientes para los formularios.'),
    });
    this.sales.quotations({ owner: customerOwner, page: 0, size: 100 }).subscribe({
      next: (page) => this.quotations.set(page.items),
      error: () => this.error.set('No se pudieron cargar las cotizaciones relacionadas.'),
    });
    this.catalog.allProducts().subscribe({
      next: (products) =>
        this.products.set(products.map((product) => this.toProductOption(product))),
      error: () => this.error.set('No se pudo cargar el catálogo de productos.'),
    });
    this.api.invoices({ page: 0, size: 100 }).subscribe({
      next: (page) => this.invoiceOptions.set(page.items),
      error: () => undefined,
    });
    this.api.purchaseOrders({ page: 0, size: 100 }).subscribe({
      next: (page) => this.orderOptions.set(page.items),
      error: () => undefined,
    });
  }

  private toProductOption(product: Product): ProductOption {
    return { id: Number(product.id), sku: product.sku, name: product.name };
  }

  // =========================================================================
  // Importación de facturas desde XML (el flujo real de la contadora)
  //
  // 1. Sube el XML que le manda su proveedor de facturación electrónica.
  // 2. El sistema lo lee y muestra qué trae: número, fecha, entidad, líneas.
  // 3. Avisa si esa factura ya está registrada (no se puede repetir), si el RUC
  //    no está en la cartera y qué códigos no existen en el catálogo.
  // 4. Al confirmar, queda registrada con el XML adjunto y los precios de compra
  //    disponibles para que el vendedor cotice.
  // =========================================================================

  readonly xmlOpen = signal(false);
  readonly xmlFile = signal<File | null>(null);
  readonly xmlPreview = signal<InvoiceXmlPreview | null>(null);
  readonly xmlResult = signal<InvoiceXmlImportResult | null>(null);
  readonly xmlBusy = signal(false);
  readonly xmlError = signal('');
  xmlType: InvoiceType = 'INVOICE';
  xmlRegisterPrices = true;
  xmlCreateCustomer = true;
  xmlNotes = '';

  /** Suma de cantidad × precio de las líneas leídas (sin IGV). */
  readonly xmlSubtotal = computed(() => {
    const preview = this.xmlPreview();
    if (!preview) return 0;
    return preview.lines.reduce((total, line) => total + line.quantity * line.unitPrice, 0);
  });

  readonly xmlCanImport = computed(() => {
    const preview = this.xmlPreview();
    if (!preview || this.xmlBusy()) return false;
    if (preview.duplicate) return false;
    return preview.customerFound || this.xmlCreateCustomer;
  });

  openXmlImport(): void {
    this.xmlOpen.set(true);
    this.xmlFile.set(null);
    this.xmlPreview.set(null);
    this.xmlResult.set(null);
    this.xmlError.set('');
    this.xmlType = 'INVOICE';
    this.xmlRegisterPrices = true;
    this.xmlCreateCustomer = true;
    this.xmlNotes = '';
  }

  closeXmlImport(): void {
    this.xmlOpen.set(false);
    this.xmlBusy.set(false);
  }

  onXmlFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.xmlResult.set(null);
    this.xmlPreview.set(null);
    this.xmlError.set('');
    this.xmlFile.set(file);
    if (file) this.readXml();
  }

  /** Lee el archivo sin guardar nada. */
  readXml(): void {
    const file = this.xmlFile();
    if (!file || this.xmlBusy()) return;
    this.xmlBusy.set(true);
    this.xmlError.set('');
    this.api.previewInvoiceXml(file, this.xmlType).subscribe({
      next: (preview) => {
        this.xmlPreview.set(preview);
        this.xmlBusy.set(false);
        if (preview.duplicate) {
          this.xmlError.set(
            `La factura ${preview.invoiceNumber} ya está registrada. No se puede cargar dos veces el mismo comprobante.`,
          );
        }
      },
      error: (error: HttpErrorResponse) => {
        this.xmlBusy.set(false);
        this.xmlPreview.set(null);
        this.xmlError.set(this.apiError(error, 'No se pudo leer el archivo XML.'));
      },
    });
  }

  /** Registra la factura leída. */
  confirmXmlImport(): void {
    const file = this.xmlFile();
    if (!file || !this.xmlCanImport()) return;
    this.xmlBusy.set(true);
    this.xmlError.set('');
    this.api
      .importInvoiceXml(file, {
        type: this.xmlType,
        registerPrices: this.xmlRegisterPrices,
        createCustomer: this.xmlCreateCustomer,
        notes: this.xmlNotes,
      })
      .subscribe({
        next: (result) => {
          this.xmlBusy.set(false);
          this.xmlResult.set(result);
          this.xmlPreview.set(null);
          this.notice.set(
            `Factura ${result.invoice.invoiceNumber} registrada desde el XML con ${result.invoice.items.length} líneas.`,
          );
          this.load();
          this.loadLookups();
        },
        error: (error: HttpErrorResponse) => {
          this.xmlBusy.set(false);
          this.xmlError.set(this.apiError(error, 'No se pudo registrar la factura del XML.'));
        },
      });
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    const status = this.statusFilter === 'ALL' ? undefined : this.statusFilter;
    const query = this.query;
    const customerId = this.customerFilter || undefined;
    const onError = (error: HttpErrorResponse) => {
      this.error.set(
        this.apiError(error, `No se pudieron cargar ${this.meta().title.toLowerCase()}.`),
      );
      this.loading.set(false);
    };
    switch (this.section()) {
      case 'invoices':
        this.api
          .invoices({
            q: query,
            status: status as InvoiceStatus,
            customerId,
            page: this.page(),
            size: this.pageSize,
          })
          .subscribe({
            next: (page) => this.setPage(this.invoices, page),
            error: onError,
          });
        break;
      case 'orders':
        this.api
          .purchaseOrders({
            q: query,
            status: status as PurchaseOrderStatus,
            customerId,
            page: this.page(),
            size: this.pageSize,
          })
          .subscribe({
            next: (page) => this.setPage(this.orders, page),
            error: onError,
          });
        break;
      case 'deliveries':
        this.api
          .deliveries({
            q: query,
            status: status as DeliveryStatus,
            customerId,
            page: this.page(),
            size: this.pageSize,
          })
          .subscribe({
            next: (page) => this.setPage(this.deliveries, page),
            error: onError,
          });
        break;
      case 'warranties':
        this.api
          .warranties({
            q: query,
            status: status as WarrantyStatus,
            customerId,
            page: this.page(),
            size: this.pageSize,
          })
          .subscribe({
            next: (page) => this.setPage(this.warranties, page),
            error: onError,
          });
        break;
    }
  }

  private setPage<T extends AccountingRecord>(
    target: WritableSignal<T[]>,
    page: { items: T[]; totalItems: number; totalPages: number },
  ): void {
    target.set(page.items);
    this.totalItems.set(page.totalItems);
    this.totalPages.set(page.totalPages);
    this.loading.set(false);
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

  newRecord(): void {
    this.error.set('');
    this.notice.set('');
    this.detail.set(null);
    this.editing.set(null);
    this.resetForms();
    this.formOpen.set(true);
  }

  editRecord(item: AccountingRecord): void {
    if (!this.canWrite() || !this.canEdit(item)) return;
    this.error.set('');
    this.notice.set('');
    this.detail.set(null);
    this.editing.set(item);
    this.resetForms(item);
    this.formOpen.set(true);
  }

  closeForm(): void {
    this.formOpen.set(false);
    this.editing.set(null);
  }

  closeFromBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.closeForm();
  }

  private resetForms(item: AccountingRecord | null = null): void {
    if (this.section() === 'invoices') {
      const value = item as InvoiceView | null;
      this.invoiceForm.reset(
        value
          ? {
              invoiceNumber: value.invoiceNumber,
              invoiceType: value.invoiceType,
              customerPublicId: value.customerPublicId,
              quotationPublicId: value.quotationPublicId ?? '',
              issueDate: value.issueDate,
              dueDate: value.dueDate ?? '',
              currencyCode: value.currencyCode,
              exchangeRateToPen: Number(value.exchangeRateToPen),
              includesTax: value.includesTax,
              taxRate: Number(value.taxRate),
              notes: value.notes ?? '',
            }
          : {
              invoiceNumber: '',
              invoiceType: 'INVOICE',
              customerPublicId: '',
              quotationPublicId: '',
              issueDate: today(),
              dueDate: '',
              currencyCode: 'PEN',
              exchangeRateToPen: 1,
              includesTax: false,
              taxRate: 0.18,
              notes: '',
            },
      );
      this.resetLines(this.invoiceLines, value?.items);
    } else if (this.section() === 'orders') {
      const value = item as PurchaseOrderView | null;
      this.orderForm.reset(
        value
          ? {
              orderNumber: value.orderNumber,
              customerPublicId: value.customerPublicId,
              quotationPublicId: value.quotationPublicId ?? '',
              invoicePublicId: value.invoicePublicId ?? '',
              issueDate: value.issueDate,
              expectedDeliveryDate: value.expectedDeliveryDate ?? '',
              deliveredDate: value.deliveredDate ?? '',
              currencyCode: value.currencyCode,
              exchangeRateToPen: Number(value.exchangeRateToPen),
              includesTax: value.includesTax,
              taxRate: Number(value.taxRate),
              notes: value.notes ?? '',
            }
          : {
              orderNumber: '',
              customerPublicId: '',
              quotationPublicId: '',
              invoicePublicId: '',
              issueDate: today(),
              expectedDeliveryDate: '',
              deliveredDate: '',
              currencyCode: 'PEN',
              exchangeRateToPen: 1,
              includesTax: false,
              taxRate: 0.18,
              notes: '',
            },
      );
      this.resetLines(this.orderLines, value?.items);
    } else if (this.section() === 'deliveries') {
      const value = item as DeliveryView | null;
      this.deliveryForm.reset(
        value
          ? {
              deliveryNumber: value.deliveryNumber,
              purchaseOrderPublicId: value.purchaseOrderPublicId,
              deliveredAt: toDateTimeLocal(value.deliveredAt),
              receivedByName: value.receivedByName ?? '',
              receivedByDocument: value.receivedByDocument ?? '',
              notes: value.notes ?? '',
            }
          : {
              deliveryNumber: '',
              purchaseOrderPublicId: '',
              deliveredAt: '',
              receivedByName: '',
              receivedByDocument: '',
              notes: '',
            },
      );
    } else {
      const value = item as WarrantyView | null;
      this.warrantyForm.reset(
        value
          ? {
              customerPublicId: value.customerPublicId,
              purchaseOrderPublicId: value.purchaseOrderPublicId ?? '',
              purchaseOrderLineNumber: value.purchaseOrderLineNumber,
              productId: value.productId,
              serialNumber: value.serialNumber ?? '',
              startsOn: value.startsOn,
              endsOn: value.endsOn,
              conditions: value.conditions ?? '',
              notes: value.notes ?? '',
            }
          : {
              customerPublicId: '',
              purchaseOrderPublicId: '',
              purchaseOrderLineNumber: null,
              productId: null,
              serialNumber: '',
              startsOn: today(),
              endsOn: today(),
              conditions: '',
              notes: '',
            },
      );
    }
    this.markPristine();
  }

  private resetLines(target: FormArray, items: AccountingItemView[] | undefined): void {
    while (target.length) target.removeAt(0);
    (items?.length ? items : [newLine()]).forEach((item) => target.push(this.createLine(item)));
  }

  private markPristine(): void {
    this.invoiceForm.markAsPristine();
    this.orderForm.markAsPristine();
    this.deliveryForm.markAsPristine();
    this.warrantyForm.markAsPristine();
    this.invoiceLines.markAsPristine();
    this.orderLines.markAsPristine();
  }

  addLine(): void {
    const target = this.section() === 'invoices' ? this.invoiceLines : this.orderLines;
    target.push(this.createLine());
  }

  removeLine(index: number): void {
    const target = this.section() === 'invoices' ? this.invoiceLines : this.orderLines;
    if (target.length > 1) target.removeAt(index);
  }

  onProductChange(line: ReturnType<typeof this.createLine>): void {
    const product = this.products().find(
      (option) => option.id === Number(line.controls.productId.value),
    );
    if (product) line.patchValue({ sku: product.sku, description: product.name, unit: 'unidad' });
  }

  onCurrencyChange(form: typeof this.invoiceForm | typeof this.orderForm): void {
    if (form.controls.currencyCode.value === 'PEN') form.controls.exchangeRateToPen.setValue(1);
  }

  save(): void {
    if (this.saving()) return;
    if (this.section() === 'invoices') {
      if (this.invoiceForm.invalid || !this.validLines(this.invoiceLines))
        return this.touch(this.invoiceForm, this.invoiceLines);
      const value = this.invoiceForm.getRawValue();
      const input: InvoiceInput = {
        ...value,
        quotationPublicId: value.quotationPublicId || null,
        dueDate: value.dueDate || null,
        items: this.toItems(this.invoiceLines),
      };
      const current = this.editing() as InvoiceView | null;
      this.persist(
        current
          ? this.api.updateInvoice(current.publicId, { ...input, version: current.version })
          : this.api.createInvoice(input),
        current ? 'Factura actualizada.' : 'Factura registrada.',
      );
    } else if (this.section() === 'orders') {
      if (this.orderForm.invalid || !this.validLines(this.orderLines))
        return this.touch(this.orderForm, this.orderLines);
      const value = this.orderForm.getRawValue();
      const input: PurchaseOrderInput = {
        ...value,
        quotationPublicId: value.quotationPublicId || null,
        invoicePublicId: value.invoicePublicId || null,
        expectedDeliveryDate: value.expectedDeliveryDate || null,
        deliveredDate: value.deliveredDate || null,
        items: this.toItems(this.orderLines),
      };
      const current = this.editing() as PurchaseOrderView | null;
      this.persist(
        current
          ? this.api.updatePurchaseOrder(current.publicId, { ...input, version: current.version })
          : this.api.createPurchaseOrder(input),
        current ? 'Orden actualizada.' : 'Orden registrada.',
      );
    } else if (this.section() === 'deliveries') {
      if (this.deliveryForm.invalid) return this.deliveryForm.markAllAsTouched();
      const value = this.deliveryForm.getRawValue();
      const input: DeliveryInput = { ...value, deliveredAt: toIsoOrNull(value.deliveredAt) };
      const current = this.editing() as DeliveryView | null;
      this.persist(
        current
          ? this.api.updateDelivery(current.publicId, { ...input, version: current.version })
          : this.api.createDelivery(input),
        current ? 'Acta actualizada.' : 'Acta registrada.',
      );
    } else {
      if (this.warrantyForm.invalid) return this.warrantyForm.markAllAsTouched();
      const value = this.warrantyForm.getRawValue();
      if (!value.customerPublicId || !value.startsOn || !value.endsOn) {
        this.warrantyForm.markAllAsTouched();
        return;
      }
      if (value.endsOn < value.startsOn) {
        this.error.set('La fecha de fin debe ser igual o posterior al inicio.');
        return;
      }
      const input: WarrantyInput = {
        customerPublicId: value.customerPublicId,
        purchaseOrderPublicId: value.purchaseOrderPublicId || null,
        purchaseOrderLineNumber: value.purchaseOrderLineNumber
          ? Number(value.purchaseOrderLineNumber)
          : null,
        productId: value.productId ? Number(value.productId) : null,
        serialNumber: value.serialNumber ?? '',
        startsOn: value.startsOn,
        endsOn: value.endsOn,
        conditions: value.conditions ?? '',
        notes: value.notes ?? '',
      };
      const current = this.editing() as WarrantyView | null;
      this.persist(
        current
          ? this.api.updateWarranty(current.publicId, { ...input, version: current.version })
          : this.api.createWarranty(input),
        current ? 'Garantía actualizada.' : 'Garantía registrada.',
      );
    }
  }

  private validLines(lines: FormArray): boolean {
    return lines.length > 0 && lines.controls.every((line) => line.valid);
  }

  private touch(form: { markAllAsTouched: () => void }, lines: FormArray): void {
    form.markAllAsTouched();
    lines.markAllAsTouched();
  }

  private toItems(lines: FormArray): AccountingItemInput[] {
    return lines.getRawValue().map((item: AccountingLineDraft) => ({
      productId: item.productId ? Number(item.productId) : null,
      sku: item.sku,
      description: item.description,
      unit: item.unit,
      quantity: Number(item.quantity),
      unitPrice: Number(item.unitPrice),
    }));
  }

  private persist(request: Observable<AccountingRecord>, successMessage: string): void {
    this.saving.set(true);
    this.error.set('');
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.editing.set(null);
        this.notice.set(successMessage);
        this.loadLookups();
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.apiError(error, `No se pudo guardar ${this.meta().singular}.`));
      },
    });
  }

  openDetail(item: AccountingRecord): void {
    this.uploadRole = this.documentRoleOptions()[0]?.value ?? '';
    this.selectedFile.set(null);
    this.uploadError.set('');
    this.detail.set(item);
    this.detailLoading.set(true);
    if (this.section() === 'invoices') this.refreshDetail(this.api.invoice(item.publicId));
    else if (this.section() === 'orders') this.refreshDetail(this.api.purchaseOrder(item.publicId));
    else if (this.section() === 'deliveries') this.refreshDetail(this.api.delivery(item.publicId));
    else this.refreshDetail(this.api.warranty(item.publicId));
  }

  private refreshDetail<T extends AccountingRecord>(request: Observable<T>): void {
    request.subscribe({
      next: (value) => {
        this.detail.set(value);
        this.detailLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.detailLoading.set(false);
        this.error.set(this.apiError(error, 'No se pudo cargar el detalle.'));
      },
    });
  }

  closeDetail(): void {
    this.detail.set(null);
    this.selectedFile.set(null);
    this.uploadError.set('');
  }

  closeDetailFromBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.closeDetail();
  }

  recordHeading(item: AccountingRecord): string {
    if (this.section() === 'invoices') return (item as InvoiceView).invoiceNumber;
    if (this.section() === 'orders') return (item as PurchaseOrderView).orderNumber;
    if (this.section() === 'deliveries') return (item as DeliveryView).deliveryNumber;
    const warranty = item as WarrantyView;
    return warranty.productSku ? `Garantía · ${warranty.productSku}` : 'Garantía registrada';
  }

  asInvoice(item: AccountingRecord): InvoiceView {
    return item as InvoiceView;
  }

  asOrder(item: AccountingRecord): PurchaseOrderView {
    return item as PurchaseOrderView;
  }

  asDelivery(item: AccountingRecord): DeliveryView {
    return item as DeliveryView;
  }

  asWarranty(item: AccountingRecord): WarrantyView {
    return item as WarrantyView;
  }

  canEdit(item: AccountingRecord): boolean {
    if (this.section() === 'invoices') return (item as InvoiceView).status === 'DRAFT';
    if (this.section() === 'orders') return (item as PurchaseOrderView).status === 'REGISTERED';
    if (this.section() === 'deliveries') return (item as DeliveryView).status === 'DRAFT';
    return (item as WarrantyView).status === 'ACTIVE';
  }

  statusActions(item: AccountingRecord): StatusAction[] {
    if (!this.canWrite()) return [];
    if (this.section() === 'invoices') {
      const status = (item as InvoiceView).status;
      return status === 'DRAFT'
        ? [
            { value: 'ISSUED', label: 'Emitir' },
            { value: 'CANCELLED', label: 'Anular' },
          ]
        : [];
    }
    if (this.section() === 'orders') {
      const status = (item as PurchaseOrderView).status;
      if (status === 'REGISTERED')
        return [
          { value: 'IN_PROGRESS', label: 'Iniciar' },
          { value: 'CANCELLED', label: 'Anular' },
        ];
      return status === 'IN_PROGRESS'
        ? [
            { value: 'DELIVERED', label: 'Marcar entregada' },
            { value: 'CANCELLED', label: 'Anular' },
          ]
        : [];
    }
    if (this.section() === 'deliveries') {
      return (item as DeliveryView).status === 'DRAFT'
        ? [
            { value: 'SIGNED', label: 'Firmar acta' },
            { value: 'CANCELLED', label: 'Anular' },
          ]
        : [];
    }
    return (item as WarrantyView).status === 'ACTIVE'
      ? [
          { value: 'EXPIRED', label: 'Marcar vencida' },
          { value: 'CLAIMED', label: 'Registrar reclamo' },
          { value: 'CANCELLED', label: 'Anular' },
        ]
      : [];
  }

  changeStatus(item: AccountingRecord, target: string): void {
    if (!this.canWrite() || this.busyAction()) return;
    const message = `¿Confirmas cambiar ${this.meta().singular} a ${statusLabel(target).toLowerCase()}?`;
    if (!window.confirm(message)) return;
    this.busyAction.set(item.publicId);
    let request: Observable<AccountingRecord>;
    if (this.section() === 'invoices') {
      request = this.api.changeInvoiceStatus(
        item.publicId,
        target as InvoiceStatus,
        (item as InvoiceView).version,
      );
    } else if (this.section() === 'orders') {
      const order = item as PurchaseOrderView;
      request = this.api.changePurchaseOrderStatus(
        item.publicId,
        target as PurchaseOrderStatus,
        order.version,
        target === 'DELIVERED' ? (order.deliveredDate ?? today()) : null,
      );
    } else if (this.section() === 'deliveries') {
      const delivery = item as DeliveryView;
      request = this.api.changeDeliveryStatus(
        item.publicId,
        target as DeliveryStatus,
        delivery.version,
        target === 'SIGNED' ? (delivery.deliveredAt ?? new Date().toISOString()) : null,
      );
    } else {
      request = this.api.changeWarrantyStatus(
        item.publicId,
        target as WarrantyStatus,
        (item as WarrantyView).version,
      );
    }
    request.subscribe({
      next: (value) => {
        this.busyAction.set(null);
        this.detail.update((current) => (current?.publicId === value.publicId ? value : current));
        this.notice.set(
          `${this.meta().singular[0].toUpperCase()}${this.meta().singular.slice(1)} actualizado.`,
        );
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.busyAction.set(null);
        this.error.set(
          this.apiError(
            error,
            'No se pudo cambiar el estado. El registro no fue marcado como actualizado.',
          ),
        );
      },
    });
  }

  documentRoleOptions(): StatusAction[] {
    if (this.section() === 'invoices')
      return [
        { value: 'PDF', label: 'PDF' },
        { value: 'XML', label: 'XML' },
        { value: 'OTHER', label: 'Otro' },
      ];
    if (this.section() === 'orders')
      return [
        { value: 'ORDER_PDF', label: 'Orden PDF' },
        { value: 'CUSTOMER_ATTACHMENT', label: 'Adjunto del cliente' },
        { value: 'OTHER', label: 'Otro' },
      ];
    if (this.section() === 'deliveries')
      return [
        { value: 'SIGNED_ACT', label: 'Acta firmada' },
        { value: 'PHOTO_PDF', label: 'Fotos en PDF' },
        { value: 'OTHER_PDF', label: 'Otro PDF' },
      ];
    return [
      { value: 'CERTIFICATE_PDF', label: 'Certificado' },
      { value: 'OTHER_PDF', label: 'Otro PDF' },
    ];
  }

  uploadAccept(): string {
    return this.section() === 'invoices' && this.uploadRole === 'XML'
      ? '.xml,application/xml,text/xml'
      : '.pdf,application/pdf';
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile.set(file);
    this.uploadError.set('');
  }

  uploadDocument(): void {
    const item = this.detail();
    const file = this.selectedFile();
    if (!item || !file || !this.uploadRole) {
      this.uploadError.set('Selecciona un archivo y su tipo documental.');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.uploadError.set('El archivo supera el límite permitido de 10 MiB.');
      return;
    }
    this.uploading.set(true);
    this.uploadProgress.set(0);
    this.uploadError.set('');
    const onResponse = (value: AccountingRecord) => {
      this.detail.set(value);
      this.load();
      this.uploading.set(false);
      this.uploadProgress.set(100);
      this.selectedFile.set(null);
      this.notice.set('Documento cargado correctamente.');
    };
    const onError = (error: HttpErrorResponse) => {
      this.uploading.set(false);
      this.uploadProgress.set(0);
      this.uploadError.set(
        this.apiError(error, 'No se pudo cargar el documento. No se guardó un falso éxito.'),
      );
    };
    if (this.section() === 'invoices')
      this.api
        .uploadInvoiceDocument(item.publicId, file, this.uploadRole, (item as InvoiceView).version)
        .subscribe({ next: (event) => this.consumeUpload(event, onResponse), error: onError });
    else if (this.section() === 'orders')
      this.api
        .uploadPurchaseOrderDocument(
          item.publicId,
          file,
          this.uploadRole,
          (item as PurchaseOrderView).version,
        )
        .subscribe({ next: (event) => this.consumeUpload(event, onResponse), error: onError });
    else if (this.section() === 'deliveries')
      this.api
        .uploadDeliveryDocument(
          item.publicId,
          file,
          this.uploadRole,
          (item as DeliveryView).version,
        )
        .subscribe({ next: (event) => this.consumeUpload(event, onResponse), error: onError });
    else
      this.api
        .uploadWarrantyDocument(
          item.publicId,
          file,
          this.uploadRole,
          (item as WarrantyView).version,
        )
        .subscribe({ next: (event) => this.consumeUpload(event, onResponse), error: onError });
  }

  private consumeUpload<T extends AccountingRecord>(
    event: HttpEvent<T>,
    onResponse: (value: T) => void,
  ): void {
    if (event.type === HttpEventType.UploadProgress && event.total) {
      this.uploadProgress.set(Math.round((event.loaded / event.total) * 100));
    } else if (event instanceof HttpResponse && event.body) {
      onResponse(event.body);
    }
  }

  downloadDocument(document: AccountingDocumentView): void {
    const item = this.detail();
    if (!item || this.busyAction()) return;
    this.busyAction.set(`download:${document.publicId}`);
    let request: Observable<Blob>;
    if (this.section() === 'invoices')
      request = this.api.downloadInvoiceDocument(item.publicId, document.publicId);
    else if (this.section() === 'orders')
      request = this.api.downloadPurchaseOrderDocument(item.publicId, document.publicId);
    else if (this.section() === 'deliveries')
      request = this.api.downloadDeliveryDocument(item.publicId, document.publicId);
    else request = this.api.downloadWarrantyDocument(item.publicId, document.publicId);
    request.subscribe({
      next: (blob) => {
        this.busyAction.set(null);
        downloadBlob(blob, document.originalName);
        this.notice.set('Documento descargado.');
      },
      error: (error: HttpErrorResponse) => {
        this.busyAction.set(null);
        this.error.set(this.apiError(error, 'No se pudo descargar el documento.'));
      },
    });
  }

  private apiError(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 401) return 'La sesión expiró. Vuelve a iniciar sesión.';
    if (error.status === 403) return 'Tu rol no permite realizar esta operación.';
    if (error.status === 404) return 'El registro ya no está disponible.';
    if (error.status === 409)
      return 'El registro cambió o la transición no está permitida. Recarga e inténtalo de nuevo.';
    if (error.status === 413) return 'El archivo supera el tamaño permitido.';
    if (error.status === 415) return 'El tipo de archivo no está permitido para este documento.';
    if (error.status === 422) return 'La operación no cumple una regla de negocio.';
    return fallback;
  }
}
