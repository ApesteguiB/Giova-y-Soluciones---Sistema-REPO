import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { SubmissionApiService } from '../core/submission-api.service';

@Component({
  selector: 'app-claims-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './claims-page.component.html',
  styleUrl: './claims-page.component.css',
})
export class ClaimsPageComponent {
  private readonly api = inject(SubmissionApiService);
  private readonly fb = inject(FormBuilder);

  readonly sending = signal(false);
  readonly successMessage = signal('');
  readonly errorMessage = signal('');
  readonly reference = signal('');
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    documentNumber: ['', [Validators.required, Validators.maxLength(20)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(100)]],
    phone: ['', [Validators.maxLength(20)]],
    address: ['', [Validators.maxLength(120)]],
    kind: ['RECLAMO' as 'RECLAMO' | 'QUEJA', Validators.required],
    goodOrService: ['', [Validators.maxLength(100)]],
    detail: ['', [Validators.required, Validators.minLength(15), Validators.maxLength(1500)]],
    requestedSolution: ['', [Validators.maxLength(800)]],
    website: [''],
  });

  private requestId = crypto.randomUUID();
  private lastSubmit = 0;

  invalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }

  submit(): void {
    this.successMessage.set('');
    this.errorMessage.set('');
    if (this.form.controls.website.value.trim()) return;
    if (Date.now() - this.lastSubmit < 5000) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage.set('Revise los campos marcados antes de enviar.');
      return;
    }
    this.lastSubmit = Date.now();
    this.sending.set(true);
    const value = this.form.getRawValue();
    this.api.submitClaim({ ...value, requestId: this.requestId, website: '' }).subscribe({
      next: (receipt) => {
        this.sending.set(false);
        this.reference.set(receipt.reference);
        this.successMessage.set(
          'Su registro fue recibido correctamente. Conserve la referencia en pantalla.',
        );
        this.form.reset({ kind: 'RECLAMO' });
        this.requestId = crypto.randomUUID();
      },
      error: (error: HttpErrorResponse) => {
        this.sending.set(false);
        this.lastSubmit = 0;
        this.errorMessage.set(
          error.status === 429
            ? 'Se alcanzó el límite temporal. Intente más tarde.'
            : error.status === 400
              ? 'Algunos datos no son válidos.'
              : 'No se pudo registrar. Revise los datos e intente nuevamente.',
        );
      },
    });
  }
}
