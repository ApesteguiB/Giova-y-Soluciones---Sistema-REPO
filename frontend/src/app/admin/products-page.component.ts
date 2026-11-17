import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  AdminApiService,
  AdminBrand,
  AdminCategory,
  AdminProduct,
} from '../core/admin-api.service';

@Component({
  selector: 'app-products-page',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule],
  templateUrl: './products-page.component.html',
  styleUrl: './admin-resource-page.css',
})
export class ProductsPageComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly fb = inject(FormBuilder);
  readonly items = signal<AdminProduct[]>([]);
  readonly brands = signal<AdminBrand[]>([]);
  readonly categories = signal<AdminCategory[]>([]);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly editing = signal<AdminProduct | null>(null);
  readonly formOpen = signal(false);
  query = '';
  brandId = '';
  categoryId = '';
  readonly form = this.fb.nonNullable.group({
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)]],
    sku: ['', [Validators.required, Validators.maxLength(80)]],
    name: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', [Validators.required, Validators.maxLength(2000)]],
    brandId: [0, [Validators.min(1)]],
    categoryId: [0, [Validators.min(1)]],
    badge: [''],
    imageUrl: [''],
    displayOrder: [0, [Validators.min(0)]],
  });
  ngOnInit() {
    this.loadOptions();
    this.load();
  }
  loadOptions() {
    this.api.brands().subscribe({ next: (v) => this.brands.set(v.filter((x) => x.active)) });
    this.api
      .categories()
      .subscribe({ next: (v) => this.categories.set(v.filter((x) => x.active)) });
  }
  load() {
    const q: { q?: string; brandId?: number; categoryId?: number } = {};
    if (this.query.trim()) q.q = this.query.trim();
    if (this.brandId) q.brandId = Number(this.brandId);
    if (this.categoryId) q.categoryId = Number(this.categoryId);
    this.api.products(q).subscribe({
      next: (v) => this.items.set(v.items),
      error: () => this.error.set('No se pudo cargar los productos.'),
    });
  }
  open(item?: AdminProduct) {
    this.error.set('');
    this.notice.set('');
    this.editing.set(item ?? null);
    this.formOpen.set(true);
    this.form.reset(
      item
        ? {
            slug: item.slug,
            sku: item.sku,
            name: item.name,
            description: item.description,
            brandId: item.brandId,
            categoryId: item.categoryId,
            badge: item.badge ?? '',
            imageUrl: item.imageUrl ?? '',
            displayOrder: item.displayOrder,
          }
        : {
            slug: '',
            sku: '',
            name: '',
            description: '',
            brandId: 0,
            categoryId: 0,
            badge: '',
            imageUrl: '',
            displayOrder: 0,
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
    const v = this.form.getRawValue();
    const current = this.editing();
    const request = current
      ? this.api.updateProduct(current.id, { ...v, version: current.version })
      : this.api.createProduct(v);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.notice.set(current ? 'Producto actualizado.' : 'Producto creado.');
        this.close();
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(
          e.status === 409
            ? 'El SKU, slug o versión entra en conflicto.'
            : 'No se pudo guardar el producto.',
        );
      },
    });
  }
  toggle(item: AdminProduct) {
    this.api.setProductStatus(item, !item.active).subscribe({
      next: () => this.load(),
      error: () => this.error.set('No se pudo cambiar el estado del producto.'),
    });
  }
}
