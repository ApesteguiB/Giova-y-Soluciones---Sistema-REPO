import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { CatalogApiService, Product } from '../core/catalog-api.service';
import {
  PriceReferenceInput,
  PriceReferenceView,
  SalesApiService,
} from '../core/sales-api.service';

interface ProductOption {
  id: number;
  name: string;
  sku: string;
}

@Component({
  selector: 'app-price-references-page',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, DecimalPipe],
  templateUrl: './price-references-page.component.html',
  styleUrl: '../admin/admin-resource-page.css',
})
export class PriceReferencesPageComponent implements OnInit {
  private readonly api = inject(SalesApiService);
  private readonly catalog = inject(CatalogApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly items = signal<PriceReferenceView[]>([]);
  readonly products = signal<ProductOption[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly editing = signal<PriceReferenceView | null>(null);
  readonly formOpen = signal(false);
  readonly role = computed(() => this.auth.session()?.roleCode ?? null);
  readonly canCreate = computed(() => this.role() === 'ADMIN' || this.role() === 'SELLER');
  readonly canEdit = computed(() => this.role() === 'ADMIN');

  // --- Buscador que filtra mientras se escribe -------------------------------
  readonly searchTerm = signal('');
  readonly selectedProduct = signal<ProductOption | null>(null);
  readonly showSuggestions = signal(false);

  /** Coincidencias por nombre o SKU, ordenadas: primero las que empiezan por el texto. */
  readonly suggestions = computed<ProductOption[]>(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) return [];
    const all = this.products();
    const scored = all
      .map((product) => {
        const name = product.name.toLowerCase();
        const sku = product.sku.toLowerCase();
        if (sku.startsWith(term) || name.startsWith(term)) return { product, score: 0 };
        if (sku.includes(term) || name.includes(term)) return { product, score: 1 };
        return null;
      })
      .filter((entry): entry is { product: ProductOption; score: number } => entry !== null);
    scored.sort((a, b) => a.score - b.score || a.product.name.localeCompare(b.product.name));
    return scored.slice(0, 10).map((entry) => entry.product);
  });

  /** Filtro del formulario de registro (mismo comportamiento que el buscador de arriba). */
  readonly formSearchTerm = signal('');
  readonly formShowSuggestions = signal(false);
  readonly formSuggestions = computed<ProductOption[]>(() => {
    const term = this.formSearchTerm().trim().toLowerCase();
    const all = this.products();
    if (!term) return all.slice(0, 10);
    return all
      .filter(
        (product) =>
          product.name.toLowerCase().includes(term) || product.sku.toLowerCase().includes(term),
      )
      .slice(0, 10);
  });
  readonly form = this.fb.nonNullable.group({
    productId: [0, [Validators.required, Validators.min(1)]],
    supplierName: ['', Validators.maxLength(180)],
    sourceReference: ['', Validators.maxLength(180)],
    currencyCode: ['PEN', Validators.required],
    unitPrice: [0, [Validators.required, Validators.min(0.0001)]],
    includesTax: [true],
    taxRate: [0.18 as number | null, Validators.min(0)],
    exchangeRateToPen: [null as number | null, Validators.min(0.000001)],
    effectiveAt: ['', Validators.required],
    notes: ['', Validators.maxLength(1000)],
  });

  ngOnInit() {
    this.loadProducts();
    this.load();
  }

  loadProducts() {
    this.catalog.allProducts().subscribe({
      next: (products) => this.products.set(products.map((product) => this.toOption(product))),
      error: () => this.error.set('No se pudo cargar el catálogo de productos.'),
    });
  }

  private toOption(product: Product): ProductOption {
    return { id: Number(product.id), name: product.name, sku: product.sku };
  }

  /** El input dispara la búsqueda: al escribir se abre la lista y se limpia el producto elegido. */
  onSearchInput(value: string): void {
    this.searchTerm.set(value);
    this.showSuggestions.set(true);
    if (this.selectedProduct() && value.trim() !== this.productLabel(this.selectedProduct()!)) {
      this.selectedProduct.set(null);
      this.load();
    }
  }

  pickProduct(product: ProductOption): void {
    this.selectedProduct.set(product);
    this.searchTerm.set(this.productLabel(product));
    this.showSuggestions.set(false);
    this.load();
  }

  clearProduct(): void {
    this.selectedProduct.set(null);
    this.searchTerm.set('');
    this.showSuggestions.set(false);
    this.load();
  }

  /** El blur se retrasa para que alcance a registrarse el clic en una sugerencia. */
  hideSuggestionsSoon(): void {
    setTimeout(() => this.showSuggestions.set(false), 150);
  }

  productLabel(product: ProductOption): string {
    return `${product.sku} · ${product.name}`;
  }

  onFormSearchInput(value: string): void {
    this.formSearchTerm.set(value);
    this.formShowSuggestions.set(true);
  }

  pickFormProduct(product: ProductOption): void {
    this.form.controls.productId.setValue(product.id);
    this.formSearchTerm.set(this.productLabel(product));
    this.formShowSuggestions.set(false);
  }

  hideFormSuggestionsSoon(): void {
    setTimeout(() => this.formShowSuggestions.set(false), 150);
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    const productId = this.selectedProduct()?.id ?? undefined;
    this.api.priceReferences({ productId, page: 0, size: 100 }).subscribe({
      next: (page) => {
        this.items.set(page.items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar los precios de referencia.');
        this.loading.set(false);
      },
    });
  }

  search() {
    this.load();
  }

  open(item?: PriceReferenceView) {
    this.error.set('');
    this.notice.set('');
    this.editing.set(item ?? null);
    this.formOpen.set(true);
    this.formShowSuggestions.set(false);
    const preselected = item
      ? this.products().find((product) => product.id === item.productId)
      : (this.selectedProduct() ?? null);
    this.formSearchTerm.set(preselected ? this.productLabel(preselected) : '');
    this.form.reset(
      item
        ? {
            productId: item.productId,
            supplierName: item.supplierName ?? '',
            sourceReference: item.sourceReference ?? '',
            currencyCode: item.currencyCode,
            unitPrice: Number(item.unitPrice),
            includesTax: item.includesTax,
            taxRate: item.taxRate === null ? null : Number(item.taxRate),
            exchangeRateToPen:
              item.exchangeRateToPen === null ? null : Number(item.exchangeRateToPen),
            effectiveAt: this.toLocalDateTime(item.effectiveAt),
            notes: item.notes ?? '',
          }
        : {
            productId: preselected?.id ?? 0,
            supplierName: '',
            sourceReference: '',
            currencyCode: 'PEN',
            unitPrice: 0,
            includesTax: true,
            taxRate: 0.18,
            exchangeRateToPen: null,
            effectiveAt: this.toLocalDateTime(new Date().toISOString()),
            notes: '',
          },
    );
  }

  close() {
    this.editing.set(null);
    this.formOpen.set(false);
  }

  save() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const value = this.form.getRawValue();
    const input: PriceReferenceInput = {
      productId: value.productId,
      supplierName: value.supplierName,
      sourceReference: value.sourceReference,
      currencyCode: value.currencyCode as PriceReferenceInput['currencyCode'],
      unitPrice: Number(value.unitPrice),
      includesTax: value.includesTax,
      taxRate: value.taxRate === null || value.taxRate === undefined ? null : Number(value.taxRate),
      exchangeRateToPen:
        value.currencyCode === 'USD' && value.exchangeRateToPen !== null
          ? Number(value.exchangeRateToPen)
          : null,
      effectiveAt: new Date(value.effectiveAt).toISOString(),
      notes: value.notes,
    };
    const current = this.editing();
    const request = current
      ? this.api.updatePriceReference(current.id, input)
      : this.api.createPriceReference(input);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.notice.set(
          current ? 'Precio de referencia actualizado.' : 'Precio de referencia registrado.',
        );
        this.close();
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(
          e.status === 409
            ? 'El precio cambió o entra en conflicto. Recargue e intente de nuevo.'
            : 'No se pudo guardar el precio de referencia.',
        );
      },
    });
  }

  displayDate(value: string) {
    return new Date(value).toLocaleString('es-PE');
  }

  private toLocalDateTime(value: string) {
    const date = new Date(value);
    const offset = date.getTimezoneOffset();
    return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16);
  }
}
