import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { CustomerInput, CustomerView, SalesApiService } from '../core/sales-api.service';

@Component({
  selector: 'app-customers-page',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule],
  templateUrl: './customers-page.component.html',
  styleUrl: '../admin/admin-resource-page.css',
})
export class CustomersPageComponent implements OnInit {
  private readonly api = inject(SalesApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly items = signal<CustomerView[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly editing = signal<CustomerView | null>(null);
  readonly formOpen = signal(false);
  readonly role = computed(() => this.auth.session()?.roleCode ?? null);
  readonly canWrite = computed(() => this.role() === 'ADMIN' || this.role() === 'SELLER');
  readonly canDeactivate = computed(() => this.role() === 'ADMIN');
  query = '';
  activeFilter: 'all' | 'active' | 'inactive' = 'active';
  readonly form = this.fb.nonNullable.group({
    customerType: ['PERSON', Validators.required],
    documentType: ['DNI', Validators.required],
    documentNumber: ['', [Validators.required, Validators.maxLength(30)]],
    businessName: ['', Validators.maxLength(180)],
    contactName: ['', Validators.maxLength(160)],
    email: ['', [Validators.email, Validators.maxLength(180)]],
    phone: ['', Validators.maxLength(40)],
    address: ['', Validators.maxLength(255)],
    notes: ['', Validators.maxLength(1000)],
  });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    const active = this.activeFilter === 'all' ? undefined : this.activeFilter === 'active';
    const owner = this.role() === 'SELLER' ? 'me' : 'all';
    this.api.customers({ q: this.query, active, owner, page: 0, size: 100 }).subscribe({
      next: (page) => {
        this.items.set(page.items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar los clientes.');
        this.loading.set(false);
      },
    });
  }

  search() {
    this.load();
  }

  open(item?: CustomerView) {
    this.error.set('');
    this.notice.set('');
    this.editing.set(item ?? null);
    this.formOpen.set(true);
    this.form.reset(
      item
        ? {
            customerType: item.customerType,
            documentType: item.documentType,
            documentNumber: item.documentNumber,
            businessName: item.businessName ?? '',
            contactName: item.contactName ?? '',
            email: item.email ?? '',
            phone: item.phone ?? '',
            address: item.address ?? '',
            notes: item.notes ?? '',
          }
        : {
            customerType: 'PERSON',
            documentType: 'DNI',
            documentNumber: '',
            businessName: '',
            contactName: '',
            email: '',
            phone: '',
            address: '',
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
    const input: CustomerInput = {
      customerType: value.customerType as CustomerInput['customerType'],
      documentType: value.documentType as CustomerInput['documentType'],
      documentNumber: value.documentNumber,
      businessName: value.businessName,
      contactName: value.contactName,
      email: value.email,
      phone: value.phone,
      address: value.address,
      notes: value.notes,
    };
    const current = this.editing();
    const request = current
      ? this.api.updateCustomer(current.publicId, { ...input, version: current.version })
      : this.api.createCustomer(input);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.notice.set(current ? 'Cliente actualizado.' : 'Cliente creado.');
        this.close();
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(
          e.status === 409
            ? 'El documento ya existe o el registro cambió. Recargue e intente de nuevo.'
            : 'No se pudo guardar el cliente.',
        );
      },
    });
  }

  toggle(item: CustomerView) {
    this.api.setCustomerStatus(item, !item.active).subscribe({
      next: () => {
        this.notice.set(item.active ? 'Cliente desactivado.' : 'Cliente activado.');
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(
          e.status === 409
            ? 'El registro cambió. Recargue e intente de nuevo.'
            : 'No se pudo cambiar el estado.',
        );
      },
    });
  }
}
