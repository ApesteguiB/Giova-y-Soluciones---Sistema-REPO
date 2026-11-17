import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { CatalogApiService, Product } from '../core/catalog-api.service';
import {
  CustomerView,
  OutcomeSource,
  ProcurementType,
  ProductPriceHistory,
  QuotationInput,
  QuotationItemInput,
  QuotationOutcome,
  QuotationStatus,
  QuotationView,
  SalesApiService,
} from '../core/sales-api.service';
import {
  allowsOcam,
  businessDaysBetween,
  calculateQuotationLinePreview,
  calculateQuotationPreview,
  competitorGapPercent,
  deadlineFromToday,
  marginPercent,
  MAX_FRAMEWORK_DAYS,
  MIN_FRAMEWORK_DAYS,
  OUTCOME_LABELS,
  OUTCOME_SOURCE_LABELS,
  outcomeRequiresReason,
  parseIsoDate,
  PROCUREMENT_HINTS,
  PROCUREMENT_LABELS,
  QuotationPreview,
  toIsoDate,
  usesBusinessDayDeadline,
} from './quotation-ui';

interface ProductOption {
  id: number;
  name: string;
  sku: string;
}

interface SafeQuotationDraft {
  procurementType: ProcurementType;
  currencyCode: 'PEN' | 'USD';
  exchangeRateToPen: number;
  includesTax: boolean;
  taxRate: number;
  validUntil: string;
  deliveryTermDays: number | null;
  warrantyMonths: number | null;
  items: Array<{
    productId: number;
    sku: string;
    description: string;
    unit: string;
    quantity: number;
    unitPrice: number;
    competitorPrice: number | null;
    purchasePrice: number | null;
  }>;
}

const DRAFT_STORAGE_KEY = 'giova.sales.quotation.draft.v1';

@Component({
  selector: 'app-quotations-page',
  standalone: true,
  imports: [DecimalPipe, FormsModule, ReactiveFormsModule],
  templateUrl: './quotations-page.component.html',
  styleUrls: ['../admin/admin-resource-page.css', './quotations-page.component.css'],
})
export class QuotationsPageComponent implements OnInit {
  private readonly api = inject(SalesApiService);
  private readonly catalog = inject(CatalogApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly items = signal<QuotationView[]>([]);
  readonly customers = signal<CustomerView[]>([]);
  readonly products = signal<ProductOption[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly formOpen = signal(false);
  readonly editing = signal<QuotationView | null>(null);
  readonly detail = signal<QuotationView | null>(null);
  readonly busyAction = signal<string | null>(null);
  readonly localDraft = signal<SafeQuotationDraft | null>(null);
  readonly role = computed(() => this.auth.session()?.roleCode ?? null);
  // Regla de negocio: solo el VENDEDOR cotiza. El administrador y el contador
  // ven las cotizaciones que registran los vendedores, pero no las crean ni las editan.
  readonly canWrite = computed(() => this.role() === 'SELLER');
  readonly canChooseOwner = computed(() => this.role() === 'ADMIN' || this.role() === 'ACCOUNTANT');

  query = '';
  statusFilter: 'ALL' | QuotationStatus = 'ALL';
  ownerFilter: 'all' | 'me' = 'all';
  readonly page = signal(0);
  readonly pageSize = 10;
  readonly totalItems = signal(0);
  readonly totalPages = signal(0);

  readonly form = this.fb.nonNullable.group({
    customerPublicId: ['', Validators.required],
    procurementType: ['PERU_COMPRAS' as ProcurementType, Validators.required],
    currencyCode: ['PEN' as 'PEN' | 'USD', Validators.required],
    exchangeRateToPen: [1, [Validators.required, Validators.min(0.000001)]],
    includesTax: [false],
    taxRate: [0.18, [Validators.required, Validators.min(0), Validators.max(1)]],
    validUntil: [''],
    frameworkDays: [MAX_FRAMEWORK_DAYS],
    deliveryTermDays: [null as number | null, Validators.min(1)],
    warrantyMonths: [null as number | null, Validators.min(0)],
    notes: ['', Validators.maxLength(1500)],
    items: this.fb.array([this.createLine()]),
  });

  ngOnInit(): void {
    this.restoreDraft();
    this.form.valueChanges.subscribe(() => {
      if (this.formOpen() && !this.editing() && this.form.dirty) this.persistDraft();
    });
    this.loadLookups();
    this.load();
  }

  get lineForms() {
    return this.form.controls.items;
  }

  get preview(): QuotationPreview {
    const value = this.form.getRawValue();
    return calculateQuotationPreview(value.items, value.includesTax, value.taxRate);
  }

  linePreview(index: number) {
    const line = this.lineForms.at(index).getRawValue();
    const value = this.form.getRawValue();
    return calculateQuotationLinePreview(
      line.quantity,
      line.unitPrice,
      value.includesTax,
      value.taxRate,
    );
  }

  loadLookups(): void {
    const owner = this.role() === 'SELLER' ? 'me' : 'all';
    this.api.customers({ active: true, owner, page: 0, size: 100 }).subscribe({
      next: (page) => this.customers.set(page.items),
      error: (error: HttpErrorResponse) =>
        this.error.set(this.apiError(error, 'No se pudieron cargar los clientes.')),
    });
    this.catalog.allProducts().subscribe({
      next: (products) => this.products.set(products.map((product) => this.toOption(product))),
      error: (error: HttpErrorResponse) =>
        this.error.set(this.apiError(error, 'No se pudo cargar el catálogo de productos.')),
    });
  }

  private toOption(product: Product): ProductOption {
    return { id: Number(product.id), name: product.name, sku: product.sku };
  }

  // --- Búsqueda de producto por línea (filtra mientras se escribe) -----------
  readonly lineSearch = signal<Record<number, string>>({});
  readonly openLine = signal<number | null>(null);

  /** Hasta 8 coincidencias por nombre o SKU; sin texto muestra las primeras del catálogo. */
  lineSuggestions(index: number): ProductOption[] {
    const term = (this.lineSearch()[index] ?? '').trim().toLowerCase();
    const all = this.products();
    if (!term) return all.slice(0, 8);
    return all
      .filter(
        (product) =>
          product.sku.toLowerCase().includes(term) || product.name.toLowerCase().includes(term),
      )
      .slice(0, 8);
  }

  onLineSearchInput(index: number, value: string): void {
    this.lineSearch.update((current) => ({ ...current, [index]: value }));
    this.openLine.set(index);
    // Escribir libremente equivale a un concepto manual hasta que se elija un producto.
    const line = this.lineForms.at(index);
    if (Number(line.controls.productId.value) > 0) {
      line.controls.productId.setValue(0);
    }
    this.form.markAsDirty();
  }

  pickLineProduct(index: number, product: ProductOption): void {
    const line = this.lineForms.at(index);
    line.patchValue({
      productId: product.id,
      sku: product.sku,
      description: product.name,
      unit: line.controls.unit.value || 'unidad',
    });
    this.lineSearch.update((current) => ({
      ...current,
      [index]: `${product.sku} · ${product.name}`,
    }));
    this.openLine.set(null);
    this.form.markAsDirty();
    // Trae el historial para que el precio no se ponga a ciegas.
    this.loadHistory(index);
    this.historyOpenLine.set(index);
  }

  useManualConcept(index: number): void {
    const line = this.lineForms.at(index);
    line.controls.productId.setValue(0);
    this.openLine.set(null);
    this.form.markAsDirty();
  }

  closeLineSoon(): void {
    setTimeout(() => this.openLine.set(null), 150);
  }

  lineSearchValue(index: number): string {
    return this.lineSearch()[index] ?? '';
  }

  private syncLineSearch(): void {
    const next: Record<number, string> = {};
    this.lineForms.controls.forEach((line, index) => {
      const value = line.getRawValue();
      const product = this.products().find((option) => option.id === Number(value.productId));
      next[index] = product
        ? `${product.sku} · ${product.name}`
        : [value.sku, value.description].filter(Boolean).join(' · ');
    });
    this.lineSearch.set(next);
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    const owner = this.role() === 'SELLER' ? 'me' : this.ownerFilter;
    this.api
      .quotations({
        q: this.query,
        status: this.statusFilter === 'ALL' ? undefined : this.statusFilter,
        procurementType: this.procurementFilter === 'ALL' ? undefined : this.procurementFilter,
        outcome: this.outcomeFilter === 'ALL' ? undefined : this.outcomeFilter,
        owner,
        page: this.page(),
        size: this.pageSize,
      })
      .subscribe({
        next: (page) => {
          this.items.set(page.items);
          this.totalItems.set(page.totalItems);
          this.totalPages.set(page.totalPages);
          this.loading.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.error.set(this.apiError(error, 'No se pudieron cargar las cotizaciones.'));
          this.loading.set(false);
        },
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

  open(item?: QuotationView): void {
    this.error.set('');
    this.notice.set('');
    this.detail.set(null);
    this.editing.set(item ?? null);
    this.formOpen.set(true);
    const draft = !item ? this.localDraft() : null;
    this.resetLines(item?.items ?? draft?.items ?? undefined);
    this.form.reset(
      item
        ? {
            customerPublicId: item.customerPublicId,
            procurementType: item.procurementType,
            currencyCode: item.currencyCode,
            exchangeRateToPen: Number(item.exchangeRateToPen),
            includesTax: item.includesTax,
            taxRate: Number(item.taxRate),
            validUntil: item.validUntil ?? '',
            frameworkDays: MAX_FRAMEWORK_DAYS,
            deliveryTermDays: item.deliveryTermDays,
            warrantyMonths: item.warrantyMonths,
            notes: item.notes ?? '',
          }
        : draft
          ? {
              customerPublicId: '',
              procurementType: draft.procurementType,
              currencyCode: draft.currencyCode,
              exchangeRateToPen: draft.exchangeRateToPen,
              includesTax: draft.includesTax,
              taxRate: draft.taxRate,
              validUntil: draft.validUntil,
              frameworkDays: MAX_FRAMEWORK_DAYS,
              deliveryTermDays: draft.deliveryTermDays,
              warrantyMonths: draft.warrantyMonths,
              notes: '',
            }
          : {
              customerPublicId: '',
              procurementType: 'PERU_COMPRAS' as ProcurementType,
              currencyCode: 'PEN',
              exchangeRateToPen: 1,
              includesTax: false,
              taxRate: 0.18,
              // Perú Compras arranca con el plazo máximo habitual: 3 días hábiles.
              validUntil: deadlineFromToday(MAX_FRAMEWORK_DAYS),
              frameworkDays: MAX_FRAMEWORK_DAYS,
              deliveryTermDays: null,
              warrantyMonths: null,
              notes: '',
            },
    );
    this.form.markAsPristine();
    if (draft)
      this.notice.set(
        'Se restauró el borrador local de líneas y montos. Cliente y notas deben completarse de nuevo.',
      );
  }

  close(): void {
    this.editing.set(null);
    this.formOpen.set(false);
  }

  closeFromBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.close();
  }

  addLine(): void {
    this.lineForms.push(this.createLine());
    this.syncLineSearch();
    this.form.markAsDirty();
  }

  removeLine(index: number): void {
    if (this.lineForms.length <= 1) return;
    this.lineForms.removeAt(index);
    this.syncLineSearch();
    this.form.markAsDirty();
  }

  onProductChange(index: number): void {
    const line = this.lineForms.at(index);
    const product = this.products().find(
      (option) => option.id === Number(line.controls.productId.value),
    );
    if (!product) return;
    line.patchValue({ sku: product.sku, description: product.name, unit: 'unidad' });
  }

  onCurrencyChange(): void {
    if (this.form.controls.currencyCode.value === 'PEN') {
      this.form.controls.exchangeRateToPen.setValue(1);
    }
  }

  save(): void {
    if (this.saving()) return;
    if (this.form.invalid || this.lineForms.length === 0) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const input = this.toInput();
    const current = this.editing();
    const request = current
      ? this.api.updateQuotation(current.publicId, { ...input, version: current.version })
      : this.api.createQuotation(input);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.clearDraft();
        this.formOpen.set(false);
        this.editing.set(null);
        this.notice.set(current ? 'Cotización actualizada.' : 'Cotización creada como borrador.');
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(
          this.apiError(error, 'No se pudo guardar la cotización. El borrador local se conserva.'),
        );
      },
    });
  }

  issue(item: QuotationView): void {
    if (!this.canWrite() || item.status !== 'DRAFT' || this.busyAction()) return;
    if (!window.confirm('La emisión congela la cotización y genera su PDF. ¿Deseas continuar?'))
      return;
    this.runAction(
      `issue:${item.publicId}`,
      () => this.api.issueQuotation(item.publicId, item.version),
      'Cotización emitida y PDF generado.',
    );
  }

  changeStatus(item: QuotationView, target: QuotationStatus): void {
    if (!this.canWrite() || this.busyAction()) return;
    const label = this.statusLabel(target).toLowerCase();
    if (!window.confirm(`¿Confirmas cambiar esta cotización a ${label}?`)) return;
    this.runAction(
      `status:${item.publicId}`,
      () => this.api.changeQuotationStatus(item.publicId, target, item.version),
      `Cotización marcada como ${label}.`,
    );
  }

  downloadPdf(item: QuotationView): void {
    if (!item.issuedPdfPublicId || this.busyAction()) return;
    this.runAction(
      `pdf:${item.publicId}`,
      () => this.api.downloadQuotationPdf(item.publicId),
      'PDF descargado.',
      (blob) => {
        const safeName = (item.quotationNumber ?? 'cotizacion').replace(/[^a-zA-Z0-9_-]/g, '_');
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${safeName}.pdf`;
        anchor.click();
        window.URL.revokeObjectURL(url);
      },
    );
  }

  openDetail(item: QuotationView): void {
    this.detail.set(item);
  }

  closeDetail(): void {
    this.detail.set(null);
  }

  displayNumber(item: QuotationView): string {
    return item.quotationNumber ?? 'Borrador sin número';
  }

  customerLabel(customer: CustomerView): string {
    return `${customer.businessName || customer.contactName || 'Sin nombre'} · ${customer.documentType} ${customer.documentNumber}`;
  }

  statusLabel(status: QuotationStatus): string {
    return {
      DRAFT: 'Borrador',
      ISSUED: 'Emitida',
      ACCEPTED: 'Aceptada',
      REJECTED: 'Rechazada',
      EXPIRED: 'Vencida',
      CANCELLED: 'Anulada',
    }[status];
  }

  statusClass(status: QuotationStatus): string {
    return `status quotation-status-${status.toLowerCase()}`;
  }

  displayDate(value: string | null): string {
    return value ? new Date(value).toLocaleDateString('es-PE') : '—';
  }

  displayDateTime(value: string | null): string {
    return value ? new Date(value).toLocaleString('es-PE') : '—';
  }

  isBusy(item: QuotationView, action: string): boolean {
    return this.busyAction() === `${action}:${item.publicId}`;
  }

  // =========================================================================
  // Historial de precios del producto
  //
  // Al elegir un producto se consulta a qué precio se vendió antes, con qué
  // precio nos ganaron cuando se perdió, y cuánto costó la última vez. Es la
  // información que hoy se busca de memoria o en archivos viejos.
  // =========================================================================

  readonly priceHistory = signal<Record<number, ProductPriceHistory>>({});
  readonly historyLoading = signal<number | null>(null);
  readonly historyOpenLine = signal<number | null>(null);

  /** Historial ya cargado para la línea, si lo hay. */
  historyFor(index: number): ProductPriceHistory | null {
    const productId = Number(this.lineForms.at(index).controls.productId.value);
    if (!productId) return null;
    return this.priceHistory()[productId] ?? null;
  }

  historyIsLoading(index: number): boolean {
    const productId = Number(this.lineForms.at(index).controls.productId.value);
    return productId > 0 && this.historyLoading() === productId;
  }

  toggleHistory(index: number): void {
    this.historyOpenLine.update((current) => (current === index ? null : index));
    const history = this.historyFor(index);
    if (!history) this.loadHistory(index);
  }

  /** Se llama al elegir producto y al abrir el panel. Cachea por producto. */
  loadHistory(index: number): void {
    const productId = Number(this.lineForms.at(index).controls.productId.value);
    if (!productId || this.priceHistory()[productId]) return;
    this.historyLoading.set(productId);
    this.api.priceHistory(productId).subscribe({
      next: (history) => {
        this.priceHistory.update((current) => ({ ...current, [productId]: history }));
        this.historyLoading.set(null);
      },
      // Un producto sin historial no es un error que valga interrumpir la carga.
      error: () => this.historyLoading.set(null),
    });
  }

  /** Copia un precio del historial al campo de la línea. */
  useHistoryPrice(index: number, price: number | null): void {
    if (price === null || price === undefined) return;
    this.lineForms.at(index).controls.unitPrice.setValue(Number(price));
    this.form.markAsDirty();
  }

  /** Resumen de una línea para la cabecera del panel, sin abrirlo. */
  historySummary(index: number): string {
    const history = this.historyFor(index);
    if (!history) return '';
    const parts: string[] = [];
    if (history.stats.timesWon > 0 && history.stats.bestWonPrice !== null) {
      parts.push(`ganada a ${history.stats.bestWonPrice.toFixed(2)}`);
    }
    if (history.stats.lowestCompetitor !== null) {
      parts.push(`competencia desde ${history.stats.lowestCompetitor.toFixed(2)}`);
    }
    if (history.lastPurchase) {
      parts.push(`costo ${history.lastPurchase.unitPrice.toFixed(2)}`);
    }
    return parts.join(' · ');
  }

  displayShortDate(value: string | null): string {
    if (!value) return '—';
    const date = parseIsoDate(value);
    return date ? date.toLocaleDateString('es-PE', { day: '2-digit', month: 'short' }) : '—';
  }

  // =========================================================================
  // Procedimiento y plazo
  // =========================================================================

  readonly procurementLabels = PROCUREMENT_LABELS;
  readonly procurementHints = PROCUREMENT_HINTS;
  readonly outcomeLabels = OUTCOME_LABELS;
  readonly outcomeSourceLabels = OUTCOME_SOURCE_LABELS;
  readonly frameworkDayOptions = [MIN_FRAMEWORK_DAYS, 2, MAX_FRAMEWORK_DAYS];

  /** Filtros del listado. */
  procurementFilter: 'ALL' | ProcurementType = 'ALL';
  outcomeFilter: 'ALL' | QuotationOutcome = 'ALL';

  /** En Perú Compras el plazo se cuenta en días hábiles; en el resto lo pone el vendedor. */
  get usesBusinessDays(): boolean {
    return usesBusinessDayDeadline(this.form.controls.procurementType.value);
  }

  get showsOcamField(): boolean {
    return allowsOcam(this.form.controls.procurementType.value);
  }

  procurementLabel(type: ProcurementType): string {
    return PROCUREMENT_LABELS[type] ?? type;
  }

  outcomeLabel(outcome: QuotationOutcome | null): string {
    return outcome ? (OUTCOME_LABELS[outcome] ?? outcome) : '—';
  }

  outcomeClass(outcome: QuotationOutcome | null): string {
    if (!outcome) return 'status';
    return `status quotation-outcome-${outcome.toLowerCase()}`;
  }

  /**
   * Al cambiar el procedimiento se reajusta el plazo: Perú Compras vuelve a su
   * cuenta en días hábiles y los demás dejan la fecha libre.
   */
  onProcurementChange(): void {
    if (this.usesBusinessDays) {
      this.applyFrameworkDays(this.form.controls.frameworkDays.value || MAX_FRAMEWORK_DAYS);
    }
    this.form.markAsDirty();
  }

  /** Escribe en el campo de fecha el resultado de contar N días hábiles desde hoy. */
  applyFrameworkDays(days: number): void {
    const value = Math.min(MAX_FRAMEWORK_DAYS, Math.max(MIN_FRAMEWORK_DAYS, Number(days) || 1));
    this.form.controls.frameworkDays.setValue(value);
    this.form.controls.validUntil.setValue(deadlineFromToday(value));
    this.form.markAsDirty();
  }

  /** Cuántos días hábiles representa la fecha elegida, para avisar si se pasa. */
  get frameworkDaysFromDate(): number | null {
    const validUntil = this.form.controls.validUntil.value;
    if (!validUntil || !this.usesBusinessDays) return null;
    return businessDaysBetween(toIsoDate(new Date()), validUntil);
  }

  get frameworkDeadlineWarning(): string {
    const days = this.frameworkDaysFromDate;
    if (days === null) return '';
    if (days < MIN_FRAMEWORK_DAYS) {
      return 'La fecha ya venció o es hoy. En Perú Compras el plazo mínimo es 1 día hábil.';
    }
    if (days > MAX_FRAMEWORK_DAYS) {
      return `Son ${days} días hábiles. Perú Compras admite hasta ${MAX_FRAMEWORK_DAYS}; el servidor va a rechazar la emisión.`;
    }
    return '';
  }

  // =========================================================================
  // Precio de competencia
  //
  // El vendedor decide si lo ve: en pantalla compartida o frente al cliente no
  // conviene tenerlo a la vista, pero el dato se guarda siempre.
  // =========================================================================

  readonly showCompetitorPrices = signal(false);

  toggleCompetitorPrices(): void {
    this.showCompetitorPrices.update((value) => !value);
  }

  /** Diferencia contra la competencia: negativo es que estamos por debajo. */
  competitorGap(index: number): number | null {
    const line = this.lineForms.at(index).getRawValue();
    return competitorGapPercent(Number(line.unitPrice), line.competitorPrice);
  }

  /** Margen sobre el costo de compra. Null cuando no hay costo cargado. */
  lineMargin(index: number): number | null {
    const line = this.lineForms.at(index).getRawValue();
    return marginPercent(Number(line.unitPrice), line.purchasePrice);
  }

  absolute(value: number): number {
    return Math.abs(value);
  }

  marginTone(margin: number | null): string {
    if (margin === null) return '';
    if (margin < 0) return 'margin-bad';
    if (margin < 8) return 'margin-warn';
    return 'margin-ok';
  }

  // =========================================================================
  // Cierre del proceso
  //
  // Todo cierre negativo exige motivo escrito y decir quién lo decidió. Sin eso
  // no se puede revisar después por qué se perdió.
  // =========================================================================

  readonly outcomeTarget = signal<QuotationView | null>(null);
  outcomeChoice: QuotationOutcome = 'NO_SELECCIONADA';
  outcomeReason = '';
  outcomeSourceChoice: OutcomeSource = 'ENTIDAD';

  openOutcome(item: QuotationView): void {
    this.error.set('');
    this.notice.set('');
    this.outcomeTarget.set(item);
    this.outcomeChoice = 'NO_SELECCIONADA';
    this.outcomeReason = '';
    this.outcomeSourceChoice = 'ENTIDAD';
  }

  closeOutcome(): void {
    this.outcomeTarget.set(null);
  }

  onOutcomeChoiceChange(): void {
    // Anular es decisión nuestra; el resto viene de la entidad.
    this.outcomeSourceChoice = this.outcomeChoice === 'ANULADA' ? 'NOSOTROS' : 'ENTIDAD';
  }

  get outcomeNeedsReason(): boolean {
    return outcomeRequiresReason(this.outcomeChoice);
  }

  get outcomeReasonMissing(): boolean {
    return this.outcomeNeedsReason && this.outcomeReason.trim().length < 5;
  }

  confirmOutcome(): void {
    const item = this.outcomeTarget();
    if (!item || this.outcomeReasonMissing || this.busyAction()) return;
    const reason = this.outcomeReason.trim() || null;
    this.runAction(
      `outcome:${item.publicId}`,
      () =>
        this.api.setQuotationOutcome(
          item.publicId,
          this.outcomeChoice,
          item.version,
          reason,
          this.outcomeSourceChoice,
        ),
      `Cotización cerrada como ${this.outcomeLabel(this.outcomeChoice).toLowerCase()}.`,
      () => this.closeOutcome(),
    );
  }

  // =========================================================================
  // OCAM
  // =========================================================================

  readonly ocamTarget = signal<QuotationView | null>(null);
  ocamNumber = '';
  ocamDate = '';

  openOcam(item: QuotationView): void {
    this.error.set('');
    this.notice.set('');
    this.ocamTarget.set(item);
    this.ocamNumber = item.ocamNumber ?? '';
    this.ocamDate = item.ocamDate ?? '';
  }

  closeOcam(): void {
    this.ocamTarget.set(null);
  }

  get ocamIncomplete(): boolean {
    return !this.ocamNumber.trim() && !this.ocamDate;
  }

  confirmOcam(): void {
    const item = this.ocamTarget();
    if (!item || this.ocamIncomplete || this.busyAction()) return;
    this.runAction(
      `ocam:${item.publicId}`,
      () =>
        this.api.setQuotationOcam(
          item.publicId,
          item.version,
          this.ocamNumber.trim() || null,
          this.ocamDate || null,
        ),
      'OCAM registrada.',
      () => this.closeOcam(),
    );
  }

  /** Solo tiene sentido pedir la OCAM cuando ya ganamos y el proceso la usa. */
  canRegisterOcam(item: QuotationView): boolean {
    return this.canWrite() && item.outcome === 'SELECCIONADA' && allowsOcam(item.procurementType);
  }

  /** Un proceso abierto es el que todavía puede cerrarse con un resultado. */
  canCloseOutcome(item: QuotationView): boolean {
    return (
      this.canWrite() && item.status !== 'DRAFT' && (item.outcome ?? 'PENDIENTE') === 'PENDIENTE'
    );
  }

  private optionalNumber(value: unknown): number | null {
    if (value === null || value === undefined || value === '') return null;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private createLine(item?: Partial<QuotationItemInput>) {
    return this.fb.nonNullable.group({
      productId: [item?.productId ?? 0, [Validators.min(0)]],
      sku: [item?.sku ?? '', [Validators.required, Validators.maxLength(80)]],
      description: [item?.description ?? '', [Validators.required, Validators.maxLength(500)]],
      unit: [item?.unit ?? 'unidad', [Validators.required, Validators.maxLength(30)]],
      quantity: [item?.quantity ?? 1, [Validators.required, Validators.min(0.0001)]],
      unitPrice: [item?.unitPrice ?? 0, [Validators.required, Validators.min(0)]],
      competitorPrice: [item?.competitorPrice ?? null, Validators.min(0)],
      purchasePrice: [item?.purchasePrice ?? null, Validators.min(0)],
    });
  }

  private resetLines(items?: ReadonlyArray<Partial<QuotationItemInput>>): void {
    while (this.lineForms.length) this.lineForms.removeAt(0);
    for (const item of items ?? [{}]) this.lineForms.push(this.createLine(item));
    this.syncLineSearch();
  }

  private toInput(): QuotationInput {
    const value = this.form.getRawValue();
    const items: QuotationItemInput[] = value.items.map((item) => ({
      productId: Number(item.productId) > 0 ? Number(item.productId) : null,
      sku: item.sku.trim(),
      description: item.description.trim(),
      unit: item.unit.trim() || 'unidad',
      quantity: Number(item.quantity),
      unitPrice: Number(item.unitPrice),
      competitorPrice: this.optionalNumber(item.competitorPrice),
      purchasePrice: this.optionalNumber(item.purchasePrice),
    }));
    return {
      customerPublicId: value.customerPublicId,
      procurementType: value.procurementType,
      currencyCode: value.currencyCode,
      exchangeRateToPen: Number(value.exchangeRateToPen),
      includesTax: value.includesTax,
      taxRate: Number(value.taxRate),
      validUntil: value.validUntil || null,
      deliveryTermDays: this.optionalNumber(value.deliveryTermDays),
      warrantyMonths: this.optionalNumber(value.warrantyMonths),
      notes: value.notes.trim() || null,
      items,
    };
  }

  private runAction<T>(
    key: string,
    request: () => Observable<T>,
    successMessage: string,
    afterSuccess?: (result: T) => void,
  ): void {
    this.busyAction.set(key);
    this.error.set('');
    request().subscribe({
      next: (result) => {
        this.busyAction.set(null);
        afterSuccess?.(result);
        this.notice.set(successMessage);
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.busyAction.set(null);
        this.error.set(this.apiError(error, 'La operación no pudo completarse.'));
      },
    });
  }

  private persistDraft(): void {
    const value = this.form.getRawValue();
    const draft: SafeQuotationDraft = {
      procurementType: value.procurementType,
      currencyCode: value.currencyCode,
      exchangeRateToPen: Number(value.exchangeRateToPen),
      includesTax: value.includesTax,
      taxRate: Number(value.taxRate),
      validUntil: value.validUntil,
      deliveryTermDays: this.optionalNumber(value.deliveryTermDays),
      warrantyMonths: this.optionalNumber(value.warrantyMonths),
      items: value.items.map((item) => ({
        productId: Number(item.productId) || 0,
        sku: item.sku.slice(0, 80),
        description: item.description.slice(0, 500),
        unit: item.unit.slice(0, 30),
        quantity: Number(item.quantity) || 0,
        unitPrice: Number(item.unitPrice) || 0,
        competitorPrice: this.optionalNumber(item.competitorPrice),
        purchasePrice: this.optionalNumber(item.purchasePrice),
      })),
    };
    try {
      sessionStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(draft));
    } catch {
      // Storage may be disabled; the API form remains usable without it.
    }
  }

  private restoreDraft(): void {
    try {
      const raw = sessionStorage.getItem(DRAFT_STORAGE_KEY);
      if (!raw) return;
      const parsed = JSON.parse(raw) as Partial<SafeQuotationDraft>;
      if (!Array.isArray(parsed.items) || parsed.items.length === 0 || parsed.items.length > 50)
        return;
      if (parsed.currencyCode !== 'PEN' && parsed.currencyCode !== 'USD') return;
      const types: ProcurementType[] = ['PERU_COMPRAS', 'DIRECTA', 'INDIRECTA', 'PRIVADA'];
      this.localDraft.set({
        procurementType: types.includes(parsed.procurementType as ProcurementType)
          ? (parsed.procurementType as ProcurementType)
          : 'PERU_COMPRAS',
        currencyCode: parsed.currencyCode,
        exchangeRateToPen:
          Number(parsed.exchangeRateToPen) > 0 ? Number(parsed.exchangeRateToPen) : 1,
        includesTax: Boolean(parsed.includesTax),
        taxRate: Math.min(1, Math.max(0, Number(parsed.taxRate) || 0)),
        validUntil: typeof parsed.validUntil === 'string' ? parsed.validUntil.slice(0, 10) : '',
        deliveryTermDays: this.optionalNumber(parsed.deliveryTermDays),
        warrantyMonths: this.optionalNumber(parsed.warrantyMonths),
        items: parsed.items.map((item) => ({
          productId: Number(item?.productId) || 0,
          sku: typeof item?.sku === 'string' ? item.sku.slice(0, 80) : '',
          description: typeof item?.description === 'string' ? item.description.slice(0, 500) : '',
          unit: typeof item?.unit === 'string' ? item.unit.slice(0, 30) : 'unidad',
          quantity: Number(item?.quantity) || 0,
          unitPrice: Number(item?.unitPrice) || 0,
          competitorPrice: this.optionalNumber(item?.competitorPrice),
          purchasePrice: this.optionalNumber(item?.purchasePrice),
        })),
      });
    } catch {
      this.localDraft.set(null);
    }
  }

  private clearDraft(): void {
    this.localDraft.set(null);
    try {
      sessionStorage.removeItem(DRAFT_STORAGE_KEY);
    } catch {
      // Ignore unavailable browser storage after the API save succeeded.
    }
  }

  private apiError(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 401)
      return 'La sesión expiró. Completa de nuevo el acceso; el borrador local seguro se conserva.';
    if (error.status === 403) return 'Tu rol no tiene permiso para esta acción.';
    if (error.status === 409)
      return 'El registro cambió o la transición no está permitida. Actualiza la lista e inténtalo de nuevo.';
    if (error.status === 0) return 'No se pudo conectar con el servidor. Inténtalo de nuevo.';
    const body = error.error as { message?: unknown } | null;
    return typeof body?.message === 'string' && body.message.trim() ? body.message : fallback;
  }
}
