import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import {
  AdminProvisioned,
  PlatformAdmin,
  PlatformApiService,
  TenantInput,
  TenantUpdate,
  TenantView,
} from '../core/platform-api.service';

@Component({
  selector: 'app-platform-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './platform-page.component.html',
  styleUrl: './platform-page.component.css',
})
export class PlatformPageComponent {
  private readonly api = inject(PlatformApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly tenants = signal<TenantView[]>([]);
  readonly selectedTenantId = signal<number | null>(null);
  readonly selectedTenant = computed(
    () => this.tenants().find((tenant) => tenant.id === this.selectedTenantId()) ?? null,
  );
  readonly currentUserName = computed(
    () => this.auth.session()?.displayName ?? 'Superadministrador',
  );
  readonly admins = signal<PlatformAdmin[]>([]);
  readonly creatingTenant = signal(false);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly temporaryPassword = signal<string | null>(null);
  readonly logoRevision = signal(0);
  readonly logoUrl = computed(() => {
    const id = this.selectedTenantId();
    return id ? `/api/v1/platform/tenants/${id}/logo?rev=${this.logoRevision()}` : '';
  });

  readonly tenantForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Z0-9][A-Z0-9_-]{2,29}$/)]],
    slug: [
      'soluciones-informaticas-ti',
      [
        Validators.required,
        Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/),
        Validators.maxLength(40),
      ],
    ],
    legalName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(200)]],
    displayName: [
      'Soluciones Informáticas TI',
      [Validators.required, Validators.minLength(2), Validators.maxLength(120)],
    ],
    taxId: ['', Validators.maxLength(20)],
    address: ['', Validators.maxLength(255)],
    contactEmail: ['', [Validators.email, Validators.maxLength(254)]],
    defaultCurrency: ['PEN', [Validators.required, Validators.pattern(/^(PEN|USD)$/)]],
    taxRate: ['0.18', [Validators.required, Validators.min(0), Validators.max(1)]],
    primaryColor: ['#E87835', [Validators.required, Validators.pattern(/^#[\da-fA-F]{6}$/)]],
    secondaryColor: ['#FFFFFF', [Validators.required, Validators.pattern(/^#[\da-fA-F]{6}$/)]],
    accentColor: ['#F3B247', [Validators.required, Validators.pattern(/^#[\da-fA-F]{6}$/)]],
    pdfHeaderText: ['', Validators.maxLength(120)],
  });

  readonly adminForm = this.fb.nonNullable.group({
    username: [
      '',
      [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(254),
        Validators.pattern(/^(?:[a-z0-9._-]{3,80}|[a-z0-9._%+-]{1,64}@[a-z0-9.-]+\.[a-z]{2,63})$/i),
      ],
    ],
    displayName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
  });

  constructor() {
    this.refreshTenants();
  }

  refreshTenants(selectId?: number): void {
    this.loading.set(true);
    this.api.listTenants().subscribe({
      next: (tenants) => {
        this.tenants.set(tenants);
        this.loading.set(false);
        if (selectId !== undefined) {
          const tenant = tenants.find((item) => item.id === selectId);
          if (tenant) this.selectTenant(tenant, true);
        }
      },
      error: (error: HttpErrorResponse) => this.fail(error, 'No se pudieron cargar las empresas.'),
    });
  }

  selectTenant(tenant: TenantView, preserveNotice = false): void {
    this.error.set('');
    if (!preserveNotice) this.notice.set('');
    if (!preserveNotice || this.selectedTenantId() !== tenant.id) {
      this.temporaryPassword.set(null);
    }
    this.creatingTenant.set(false);
    this.selectedTenantId.set(tenant.id);
    this.admins.set([]);
    this.tenantForm.reset({
      code: tenant.code,
      slug: tenant.slug,
      legalName: tenant.legalName,
      displayName: tenant.displayName,
      taxId: tenant.taxId ?? '',
      address: tenant.address ?? '',
      contactEmail: tenant.contactEmail ?? '',
      defaultCurrency: tenant.defaultCurrency,
      taxRate: String(tenant.taxRate),
      primaryColor: tenant.primaryColor,
      secondaryColor: tenant.secondaryColor,
      accentColor: tenant.accentColor,
      pdfHeaderText: tenant.pdfHeaderText ?? '',
    });
    this.adminForm.reset({ username: '', displayName: '' });
    this.api.listAdmins(tenant.id).subscribe({
      next: (admins) => this.admins.set(admins),
      error: (error: HttpErrorResponse) =>
        this.fail(error, 'No se pudieron cargar las cuentas ADMIN.'),
    });
  }

  newTenant(): void {
    this.selectedTenantId.set(null);
    this.admins.set([]);
    this.creatingTenant.set(true);
    this.temporaryPassword.set(null);
    this.error.set('');
    this.notice.set('');
    this.tenantForm.reset({
      code: '',
      slug: 'soluciones-informaticas-ti',
      legalName: '',
      displayName: 'Soluciones Informáticas TI',
      taxId: '',
      address: '',
      contactEmail: '',
      defaultCurrency: 'PEN',
      taxRate: '0.18',
      primaryColor: '#E87835',
      secondaryColor: '#FFFFFF',
      accentColor: '#F3B247',
      pdfHeaderText: '',
    });
  }

  saveTenant(): void {
    if (this.saving()) return;
    this.error.set('');
    this.notice.set('');
    const normalized = this.tenantForm.getRawValue();
    for (const field of Object.keys(normalized) as (keyof typeof normalized)[]) {
      normalized[field] = String(normalized[field] ?? '').trim();
    }
    normalized.code = normalized.code.toUpperCase();
    normalized.slug = normalized.slug.toLowerCase();
    normalized.contactEmail = normalized.contactEmail.toLowerCase();
    normalized.primaryColor = normalized.primaryColor.toUpperCase();
    normalized.secondaryColor = normalized.secondaryColor.toUpperCase();
    normalized.accentColor = normalized.accentColor.toUpperCase();
    this.tenantForm.patchValue(normalized);
    if (this.tenantForm.invalid) {
      this.tenantForm.markAllAsTouched();
      this.fail(null, 'Revise los campos indicados antes de guardar la empresa.');
      return;
    }
    const values = this.tenantForm.getRawValue();
    const payload: TenantInput = {
      code: values.code.trim().toUpperCase(),
      slug: values.slug.trim().toLowerCase(),
      legalName: values.legalName.trim(),
      displayName: values.displayName.trim(),
      taxId: this.optional(values.taxId),
      address: this.optional(values.address),
      contactEmail: this.optional(values.contactEmail)?.toLowerCase() ?? null,
      defaultCurrency: values.defaultCurrency as 'PEN' | 'USD',
      taxRate: Number(values.taxRate),
      primaryColor: values.primaryColor.toUpperCase(),
      secondaryColor: values.secondaryColor.toUpperCase(),
      accentColor: values.accentColor.toUpperCase(),
      pdfHeaderText: this.optional(values.pdfHeaderText),
    };
    this.saving.set(true);
    if (this.creatingTenant()) {
      this.api.createTenant(payload).subscribe({
        next: (tenant) => {
          this.saving.set(false);
          this.creatingTenant.set(false);
          this.notice.set(
            'Empresa creada. Ahora puede cargar su logo y registrar su cuenta ADMIN.',
          );
          this.refreshTenants(tenant.id);
        },
        error: (error: HttpErrorResponse) => this.fail(error, 'No se pudo crear la empresa.'),
      });
      return;
    }
    const selected = this.selectedTenant();
    if (!selected) {
      this.saving.set(false);
      this.fail(null, 'Seleccione una empresa para guardar los cambios.');
      return;
    }
    const update: TenantUpdate = { ...payload, version: selected.version };
    this.api.updateTenant(selected.id, update).subscribe({
      next: (tenant) => {
        this.saving.set(false);
        this.notice.set('Cambios guardados.');
        this.refreshTenants(tenant.id);
      },
      error: (error: HttpErrorResponse) => this.fail(error, 'No se pudieron guardar los cambios.'),
    });
  }

  toggleTenant(): void {
    const tenant = this.selectedTenant();
    if (!tenant) return;
    const active = !tenant.active;
    if (
      !active &&
      !window.confirm(
        `Desactivar ${tenant.displayName}? Sus trabajadores no podrán iniciar sesión.`,
      )
    ) {
      return;
    }
    this.api.setTenantActive(tenant.id, active, tenant.version).subscribe({
      next: (updated) => {
        this.notice.set(
          active
            ? 'Empresa activada.'
            : 'Empresa desactivada; sus trabajadores no podrán iniciar sesión.',
        );
        this.refreshTenants(updated.id);
      },
      error: (error: HttpErrorResponse) =>
        this.fail(error, 'No se pudo cambiar el estado de la empresa.'),
    });
  }

  uploadLogo(event: Event): void {
    const tenant = this.selectedTenant();
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    if (!tenant || !file) return;
    this.error.set('');
    if (file.size > 5 * 1024 * 1024 || !['image/png', 'image/jpeg'].includes(file.type)) {
      this.fail(null, 'Seleccione un PNG o JPEG de hasta 5 MiB.');
      input.value = '';
      return;
    }
    this.api.uploadLogo(tenant.id, file).subscribe({
      next: (updated) => {
        this.logoRevision.update((revision) => revision + 1);
        this.notice.set('Logo cargado y validado.');
        this.replaceTenant(updated);
        input.value = '';
      },
      error: (error: HttpErrorResponse) => {
        this.fail(error, 'No se pudo cargar el logo. Use un PNG o JPEG válido.');
        input.value = '';
      },
    });
  }

  async useReferenceLogo(): Promise<void> {
    const tenant = this.selectedTenant();
    if (!tenant || tenant.slug !== 'soluciones-informaticas-ti') return;
    this.error.set('');
    try {
      const response = await fetch('/assets/soluciones-informaticas-ti.svg', {
        credentials: 'same-origin',
      });
      if (!response.ok) throw new Error('No se pudo leer el logotipo vectorial.');
      const canvas = document.createElement('canvas');
      canvas.width = 1520;
      canvas.height = 600;
      const context = canvas.getContext('2d');
      if (!context) throw new Error('El navegador no pudo preparar el logotipo.');
      const imageUrl = URL.createObjectURL(await response.blob());
      try {
        const image = new Image();
        image.src = imageUrl;
        await image.decode();
        context.fillStyle = '#FFFFFF';
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.drawImage(image, 0, 0, canvas.width, canvas.height);
      } finally {
        URL.revokeObjectURL(imageUrl);
      }
      const png = await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob(
          (blob) => (blob ? resolve(blob) : reject(new Error('No se pudo exportar el logotipo.'))),
          'image/png',
        );
      });
      const file = new File([png], 'soluciones-informaticas-ti.png', { type: 'image/png' });
      this.api.uploadLogo(tenant.id, file).subscribe({
        next: (updated) => {
          this.logoRevision.update((revision) => revision + 1);
          this.notice.set('Logotipo vectorial optimizado cargado.');
          this.replaceTenant(updated);
        },
        error: (error: HttpErrorResponse) =>
          this.fail(error, 'No se pudo cargar el logotipo vectorial.'),
      });
    } catch {
      this.fail(null, 'No se pudo preparar el logotipo vectorial.');
    }
  }

  createOrLinkAdmin(): void {
    const tenant = this.selectedTenant();
    if (!tenant) return;
    this.error.set('');
    this.adminForm.patchValue({
      username: this.adminForm.controls.username.value.trim().toLowerCase(),
      displayName: this.adminForm.controls.displayName.value.trim(),
    });
    if (this.adminForm.invalid) {
      this.adminForm.markAllAsTouched();
      this.fail(null, 'Revise el usuario y el nombre visible de la cuenta ADMIN.');
      return;
    }
    const { username, displayName } = this.adminForm.getRawValue();
    this.api.createOrLinkAdmin(tenant.id, username.trim(), displayName.trim()).subscribe({
      next: (result) => {
        this.showProvisioned(
          result,
          result.created
            ? 'Cuenta ADMIN creada.'
            : 'Cuenta ADMIN vinculada. Conserva su contraseña actual; use Restablecer contraseña si necesita una nueva.',
        );
        this.adminForm.reset({ username: '', displayName: '' });
        this.api.listAdmins(tenant.id).subscribe({ next: (admins) => this.admins.set(admins) });
        this.refreshTenants(tenant.id);
      },
      error: (error: HttpErrorResponse) =>
        this.fail(error, 'No se pudo crear o vincular la cuenta ADMIN.'),
    });
  }

  toggleAdmin(admin: PlatformAdmin): void {
    const tenant = this.selectedTenant();
    if (!tenant) return;
    const enabled = !admin.enabled;
    if (
      !enabled &&
      !window.confirm(`Desactivar la cuenta de ${admin.displayName} en ${tenant.displayName}?`)
    )
      return;
    this.api.setAdminEnabled(tenant.id, admin.workerId, enabled, admin.version).subscribe({
      next: () => {
        this.notice.set(enabled ? 'Membresía ADMIN activada.' : 'Membresía ADMIN desactivada.');
        this.api.listAdmins(tenant.id).subscribe({ next: (admins) => this.admins.set(admins) });
      },
      error: (error: HttpErrorResponse) =>
        this.fail(error, 'No se pudo cambiar la membresía ADMIN.'),
    });
  }

  resetAdminPassword(admin: PlatformAdmin): void {
    const tenant = this.selectedTenant();
    if (!tenant || !window.confirm(`Generar una contraseña temporal para ${admin.displayName}?`))
      return;
    this.api.resetAdminPassword(tenant.id, admin.workerId).subscribe({
      next: (result) => this.showProvisioned(result, 'Contraseña temporal generada.'),
      error: (error: HttpErrorResponse) =>
        this.fail(error, 'No se pudo restablecer la contraseña.'),
    });
  }

  clearTemporaryPassword(): void {
    this.temporaryPassword.set(null);
  }

  tenantFieldError(field: keyof typeof this.tenantForm.controls): string | null {
    const control = this.tenantForm.controls[field];
    if (!control.touched || !control.errors) return null;
    if (control.hasError('required')) return 'Este campo es obligatorio.';
    if (control.hasError('email')) return 'Ingrese un correo válido o deje el campo vacío.';
    if (control.hasError('minlength')) {
      return `Use al menos ${control.getError('minlength').requiredLength} caracteres.`;
    }
    if (control.hasError('maxlength')) {
      return `Use como máximo ${control.getError('maxlength').requiredLength} caracteres.`;
    }
    if (field === 'code') return 'Use de 3 a 30 letras, números, guion o guion bajo.';
    if (field === 'slug') return 'Use letras sin tildes, números y guiones entre palabras.';
    if (field === 'taxRate') return 'Ingrese una tasa entre 0 y 1; por ejemplo, 0.18.';
    if (field === 'defaultCurrency') return 'Seleccione PEN o USD.';
    return 'Use un color hexadecimal de seis dígitos: #RRGGBB.';
  }

  setColor(field: 'primaryColor' | 'secondaryColor' | 'accentColor', event: Event): void {
    const color = (event.currentTarget as HTMLInputElement).value.toUpperCase();
    this.tenantForm.controls[field].setValue(color);
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigate(['/trabajadores/login']),
      error: () => void this.router.navigate(['/trabajadores/login']),
    });
  }

  private showProvisioned(result: AdminProvisioned, message: string): void {
    this.temporaryPassword.set(result.temporaryPassword);
    this.notice.set(message);
    this.error.set('');
  }

  private replaceTenant(updated: TenantView): void {
    this.tenants.update((items) =>
      items.map((tenant) => (tenant.id === updated.id ? updated : tenant)),
    );
  }

  private optional(value: string): string | null {
    const trimmed = value.trim();
    return trimmed ? trimmed : null;
  }

  private fail(error: HttpErrorResponse | null, fallback: string): void {
    this.loading.set(false);
    this.saving.set(false);
    const detail = error?.error?.detail;
    this.error.set(typeof detail === 'string' && detail.length < 240 ? detail : fallback);
  }
}
