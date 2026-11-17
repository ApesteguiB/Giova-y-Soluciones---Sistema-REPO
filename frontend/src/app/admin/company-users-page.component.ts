import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnDestroy, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  AdminWorker,
  AdminWorkersApiService,
  CompanyWorkerRole,
  WorkerInput,
} from '../core/admin-workers-api.service';

@Component({
  selector: 'app-company-users-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './company-users-page.component.html',
  styleUrl: './company-users-page.component.css',
})
export class CompanyUsersPageComponent implements OnDestroy {
  private readonly api = inject(AdminWorkersApiService);
  private readonly fb = inject(FormBuilder);

  readonly workers = signal<AdminWorker[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly mode = signal<'create' | 'edit' | null>(null);
  readonly editingWorker = signal<AdminWorker | null>(null);
  readonly temporaryPassword = signal<string | null>(null);
  readonly temporaryPasswordFor = signal('');
  readonly error = signal('');
  readonly notice = signal('');

  readonly createForm = this.fb.nonNullable.group({
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
    roleCode: ['ACCOUNTANT' as CompanyWorkerRole, Validators.required],
  });

  readonly editForm = this.fb.nonNullable.group({
    displayName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
  });

  constructor() {
    this.load();
  }

  ngOnDestroy(): void {
    this.clearTemporaryPassword();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.list().subscribe({
      next: (items) => {
        this.workers.set(items);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => this.fail(error, 'No se pudieron cargar las cuentas.'),
    });
  }

  openCreate(): void {
    this.resetFeedback();
    this.createForm.reset({ username: '', displayName: '', roleCode: 'ACCOUNTANT' });
    this.editingWorker.set(null);
    this.mode.set('create');
  }

  openEdit(worker: AdminWorker): void {
    this.resetFeedback();
    this.editingWorker.set(worker);
    this.editForm.reset({ displayName: worker.displayName });
    this.mode.set('edit');
  }

  closeForm(): void {
    this.mode.set(null);
    this.editingWorker.set(null);
  }

  create(): void {
    this.resetFeedback();
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    const values = this.createForm.getRawValue();
    const input: WorkerInput = {
      username: values.username.trim().toLowerCase(),
      displayName: values.displayName.trim(),
      roleCode: values.roleCode,
    };
    this.saving.set(true);
    this.api.create(input).subscribe({
      next: (created) => {
        this.saving.set(false);
        this.mode.set(null);
        this.showTemporaryPassword(created.worker, created.temporaryPassword);
        this.notice.set('Cuenta creada. La contraseña temporal se muestra una sola vez.');
        this.load();
      },
      error: (error: HttpErrorResponse) => this.fail(error, 'No se pudo crear la cuenta.'),
    });
  }

  saveDisplayName(): void {
    this.resetFeedback();
    const worker = this.editingWorker();
    if (!worker) return;
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.api
      .update(worker.workerId, this.editForm.controls.displayName.value.trim(), worker.version)
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.closeForm();
          this.notice.set('Nombre visible actualizado.');
          this.load();
        },
        error: (error: HttpErrorResponse) => this.fail(error, 'No se pudo actualizar el nombre.'),
      });
  }

  toggleEnabled(worker: AdminWorker): void {
    const enabled = !worker.enabled;
    if (
      !enabled &&
      !window.confirm(`¿Desactivar la cuenta de ${worker.displayName} en esta empresa?`)
    ) {
      return;
    }
    this.resetFeedback();
    this.saving.set(true);
    this.api.setEnabled(worker.workerId, enabled, worker.version).subscribe({
      next: () => {
        this.saving.set(false);
        this.notice.set(
          enabled ? 'Cuenta activada en esta empresa.' : 'Cuenta desactivada en esta empresa.',
        );
        this.load();
      },
      error: (error: HttpErrorResponse) =>
        this.fail(error, 'No se pudo cambiar el estado de la cuenta.'),
    });
  }

  resetPassword(worker: AdminWorker): void {
    if (!window.confirm(`¿Generar una nueva contraseña temporal para ${worker.displayName}?`))
      return;
    this.resetFeedback();
    this.saving.set(true);
    this.api.resetPassword(worker.workerId).subscribe({
      next: (reset) => {
        this.saving.set(false);
        this.showTemporaryPassword(reset.worker, reset.temporaryPassword);
        this.notice.set('Contraseña temporal generada. Se muestra una sola vez.');
      },
      error: (error: HttpErrorResponse) =>
        this.fail(error, 'No se pudo restablecer la contraseña.'),
    });
  }

  clearTemporaryPassword(): void {
    this.temporaryPassword.set(null);
    this.temporaryPasswordFor.set('');
  }

  roleLabel(role: CompanyWorkerRole): string {
    return role === 'ACCOUNTANT' ? 'Contador' : 'Vendedor';
  }

  private showTemporaryPassword(worker: AdminWorker, password: string): void {
    this.temporaryPassword.set(password);
    this.temporaryPasswordFor.set(`${worker.displayName} · ${worker.username}`);
  }

  private fail(error: HttpErrorResponse, fallback: string): void {
    this.saving.set(false);
    this.loading.set(false);
    this.error.set(
      error.status === 409
        ? 'El usuario no está disponible o la cuenta cambió. Actualice la lista y vuelva a intentar.'
        : fallback,
    );
  }

  private resetFeedback(): void {
    this.error.set('');
    this.notice.set('');
  }
}
