import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminApiService, AdminBrand } from '../core/admin-api.service';

@Component({
  selector: 'app-brands-page',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule],
  templateUrl: './brands-page.component.html',
  styleUrl: './admin-resource-page.css',
})
export class BrandsPageComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly fb = inject(FormBuilder);
  readonly items = signal<AdminBrand[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly editing = signal<AdminBrand | null>(null);
  readonly formOpen = signal(false);
  query = '';
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)]],
    logoUrl: [''],
    displayColor: [''],
    featured: [false],
    displayOrder: [0, [Validators.min(0)]],
  });
  ngOnInit() {
    this.load();
  }
  load() {
    this.loading.set(true);
    this.api.brands(this.query).subscribe({
      next: (v) => {
        this.items.set(v);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar las marcas.');
        this.loading.set(false);
      },
    });
  }
  search() {
    this.load();
  }
  open(item?: AdminBrand) {
    this.error.set('');
    this.notice.set('');
    this.editing.set(item ?? null);
    this.formOpen.set(true);
    this.form.reset(
      item
        ? {
            name: item.name,
            slug: item.slug,
            logoUrl: item.logoUrl ?? '',
            displayColor: item.displayColor ?? '',
            featured: item.featured,
            displayOrder: item.displayOrder,
          }
        : { name: '', slug: '', logoUrl: '', displayColor: '', featured: false, displayOrder: 0 },
    );
  }
  close() {
    this.editing.set(null);
    this.formOpen.set(false);
    this.form.reset({
      name: '',
      slug: '',
      logoUrl: '',
      displayColor: '',
      featured: false,
      displayOrder: 0,
    });
  }
  save() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const value = this.form.getRawValue();
    const current = this.editing();
    const request = current
      ? this.api.updateBrand(current.id, { ...value, version: current.version })
      : this.api.createBrand(value);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.notice.set(current ? 'Marca actualizada.' : 'Marca creada.');
        this.close();
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(
          e.status === 409
            ? 'El registro cambió o entra en conflicto. Recargue e intente de nuevo.'
            : 'No se pudo guardar la marca.',
        );
      },
    });
  }
  toggle(item: AdminBrand) {
    this.api.setBrandStatus(item, !item.active).subscribe({
      next: () => this.load(),
      error: (e: HttpErrorResponse) =>
        this.error.set(
          e.status === 409
            ? 'No se puede cambiar: hay un conflicto o productos vinculados.'
            : 'No se pudo cambiar el estado.',
        ),
    });
  }
}
