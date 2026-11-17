import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-change-password-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './change-password-page.component.html',
  styleUrl: './change-password-page.component.css',
})
export class ChangePasswordPageComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly saving = signal(false);
  readonly passwordChanged = signal(false);
  readonly selectedMembershipId = signal<number | null>(null);
  readonly error = signal('');
  readonly notice = signal('');

  readonly form = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required, Validators.maxLength(200)]],
    newPassword: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(200)]],
    confirmation: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(200)]],
  });

  get memberships() {
    return this.auth.session()?.memberships ?? [];
  }

  submit(): void {
    this.error.set('');
    this.notice.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword, confirmation } = this.form.getRawValue();
    if (newPassword !== confirmation) {
      this.error.set('La confirmación no coincide con la nueva contraseña.');
      return;
    }
    this.saving.set(true);
    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: (session) => {
        this.saving.set(false);
        this.form.reset();
        if (session.membershipSelectionRequired && session.memberships?.length) {
          this.passwordChanged.set(true);
          this.notice.set(
            'Contraseña actualizada. Seleccione la empresa en la que desea ingresar.',
          );
          this.selectedMembershipId.set(session.memberships[0].membershipId);
          return;
        }
        void this.openWorkspace(session.roleCode);
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(
          error.status === 409
            ? 'La cuenta cambió durante la operación. Inicie sesión de nuevo.'
            : 'No se pudo cambiar la contraseña. Verifique la contraseña actual y vuelva a intentar.',
        );
      },
    });
  }

  selectMembership(): void {
    const membershipId = this.selectedMembershipId();
    if (!membershipId) return;
    this.saving.set(true);
    this.error.set('');
    this.auth.selectMembership(membershipId).subscribe({
      next: (session) => {
        this.saving.set(false);
        void this.openWorkspace(session.roleCode);
      },
      error: () => {
        this.saving.set(false);
        this.error.set(
          'La empresa seleccionada ya no está disponible. Actualice e intente otra vez.',
        );
      },
    });
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigateByUrl('/trabajadores/login'),
      error: () => this.error.set('No se pudo cerrar la sesión. Vuelva a intentarlo.'),
    });
  }

  private async openWorkspace(roleCode: string | null): Promise<void> {
    await this.router.navigateByUrl(roleCode === 'SUPERADMIN' ? '/plataforma' : '/operaciones');
  }
}
