import { Component, inject, signal } from '@angular/core';
import { NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { PublicSiteService } from '../core/public-site.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, NgIf],
  templateUrl: './login-page.component.html',
  styleUrl: './login-page.component.css',
})
export class LoginPageComponent {
  readonly site = inject(PublicSiteService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  readonly sending = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(254)]],
    password: ['', [Validators.required]],
  });

  submit(): void {
    this.error.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.sending.set(true);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => {
        this.sending.set(false);
        if (this.auth.session()?.mustChangePassword) {
          void this.router.navigateByUrl('/trabajadores/cambiar-contrasena');
          return;
        }
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        const role = this.auth.session()?.roleCode;
        const internalReturnUrl =
          role === 'SUPERADMIN'
            ? returnUrl && (returnUrl === '/plataforma' || returnUrl.startsWith('/plataforma/'))
              ? returnUrl
              : '/plataforma'
            : returnUrl &&
                (returnUrl === '/admin' ||
                  returnUrl.startsWith('/admin/') ||
                  returnUrl === '/operaciones' ||
                  returnUrl.startsWith('/operaciones/'))
              ? returnUrl
              : '/operaciones';
        void this.router.navigateByUrl(internalReturnUrl);
      },
      error: (error: HttpErrorResponse) => {
        this.sending.set(false);
        this.error.set(
          error.status === 429
            ? 'Demasiados intentos. Espere unos minutos.'
            : 'No se pudo validar el acceso. Revise sus datos.',
        );
      },
    });
  }
}
