import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { AdminApiService, AdminCategory } from '../core/admin-api.service';

@Component({
  selector: 'app-categories-page',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule],
  templateUrl: './categories-page.component.html',
  styleUrl: './admin-resource-page.css',
})
export class CategoriesPageComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly fb = inject(FormBuilder);
  readonly items = signal<AdminCategory[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly editing = signal<AdminCategory | null>(null);
  readonly formOpen = signal(false);
  query = '';
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)]],
    iconKey: ['', [Validators.required, Validators.maxLength(40)]],
    displayOrder: [0, [Validators.min(0)]],
  });
  ngOnInit() {
    this.load();
  }
  load() {
    this.loading.set(true);
    this.api.categories(this.query).subscribe({
      next: (v) => {
        this.items.set(v);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar las categorías.');
        this.loading.set(false);
      },
    });
  }
  open(item?: AdminCategory) {
    this.error.set('');
    this.notice.set('');
    this.editing.set(item ?? null);
    this.formOpen.set(true);
    this.form.reset(
      item
        ? {
            name: item.name,
            slug: item.slug,
            iconKey: item.iconKey,
            displayOrder: item.displayOrder,
          }
        : { name: '', slug: '', iconKey: '', displayOrder: 0 },
    );
  }
  close() {
    this.editing.set(null);
    this.formOpen.set(false);
    this.form.reset({ name: '', slug: '', iconKey: '', displayOrder: 0 });
  }
  save() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const current = this.editing();
    const request = current
      ? this.api.updateCategory(current.id, { ...value, version: current.version })
      : this.api.createCategory(value);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.notice.set(current ? 'Categoría actualizada.' : 'Categoría creada.');
        this.close();
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(
          e.status === 409
            ? 'El registro entra en conflicto con otro dato.'
            : 'No se pudo guardar la categoría.',
        );
      },
    });
  }
  toggle(item: AdminCategory) {
    this.api.setCategoryStatus(item, !item.active).subscribe({
      next: () => this.load(),
      error: (e: HttpErrorResponse) =>
        this.error.set(
          e.status === 409
            ? 'No se puede desactivar porque tiene productos activos.'
            : 'No se pudo cambiar el estado.',
        ),
    });
  }
}
