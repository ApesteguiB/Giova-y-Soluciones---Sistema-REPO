import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { SubmissionApiService } from '../core/submission-api.service';
import { PublicSiteService } from '../core/public-site.service';
import { RevealDirective } from '../shared/reveal.directive';

@Component({
  selector: 'app-contact-page',
  standalone: true,
  imports: [ReactiveFormsModule, RevealDirective],
  templateUrl: './contact-page.component.html',
  styleUrl: './contact-page.component.css',
})
export class ContactPageComponent implements OnInit {
  private readonly api = inject(SubmissionApiService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  readonly site = inject(PublicSiteService);
  private readonly sanitizer = inject(DomSanitizer);
  readonly sending = signal(false);
  readonly successMessage = signal('');
  readonly errorMessage = signal('');
  readonly reference = signal('');

  /**
   * El mapa apunta a la dirección de la empresa del dominio. Angular exige
   * marcar la URL como confiable para poder usarla en un iframe.
   */
  readonly mapEmbedUrl = computed<SafeResourceUrl | null>(() => {
    const address = this.site.site().address;
    if (!address) return null;
    const query = encodeURIComponent(address);
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      `https://maps.google.com/maps?q=${query}&z=16&output=embed`,
    );
  });

  readonly subjects = [
    'Cotización de productos',
    'Tóners HP / Kyocera',
    'Equipos informáticos',
    'Redes y seguridad',
    'Servicios técnicos',
    'Otro',
  ];

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    company: ['', [Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(100)]],
    phone: ['', [Validators.maxLength(20)]],
    subject: [this.subjects[0], [Validators.required, Validators.maxLength(80)]],
    message: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(1000)]],
    website: [''],
  });

  private requestId = crypto.randomUUID();
  private lastSubmit = 0;

  ngOnInit(): void {
    const subject = this.route.snapshot.queryParamMap.get('asunto');
    if (subject && this.subjects.includes(subject)) this.form.controls.subject.setValue(subject);
  }

  invalid(field: 'name' | 'company' | 'email' | 'phone' | 'subject' | 'message'): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }

  submit(): void {
    this.successMessage.set('');
    this.errorMessage.set('');

    if (this.form.controls.website.value.trim() !== '') return;

    // Anti-spam: no permitir dos envíos en 5 segundos.
    const now = Date.now();
    if (now - this.lastSubmit < 5000) return;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage.set('Por favor revise los campos marcados antes de enviar.');
      return;
    }

    this.lastSubmit = now;
    this.sending.set(true);
    const value = this.form.getRawValue();
    this.api
      .submitContact({
        requestId: this.requestId,
        name: value.name.trim(),
        company: value.company.trim(),
        email: value.email.trim(),
        phone: value.phone.trim(),
        subject: value.subject,
        message: value.message.trim(),
        website: '',
      })
      .subscribe({
        next: (receipt) => {
          this.sending.set(false);
          this.reference.set(receipt.reference);
          this.successMessage.set(
            'Su solicitud fue registrada correctamente. Conserve la referencia para cualquier consulta.',
          );
          this.form.reset({ subject: this.subjects[0] });
          this.requestId = crypto.randomUUID();
        },
        error: (error: HttpErrorResponse) => {
          this.sending.set(false);
          this.lastSubmit = 0;
          this.errorMessage.set(
            error.status === 429
              ? 'Recibimos demasiadas solicitudes desde esta conexión. Intente en unos minutos o escríbanos por WhatsApp.'
              : error.status === 400
                ? 'Algunos datos no son válidos. Revise el formulario e intente nuevamente.'
                : 'No se pudo enviar en este momento. Intente nuevamente o escríbanos por WhatsApp.',
          );
        },
      });
  }
}
