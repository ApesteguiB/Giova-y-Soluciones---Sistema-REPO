import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { TenantView } from '../core/platform-api.service';
import { PlatformPageComponent } from './platform-page.component';

describe('PlatformPageComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlatformPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            session: () => ({
              id: 1,
              username: 'root',
              displayName: 'Plataforma',
              roleCode: 'SUPERADMIN',
            }),
            logout: () => of(undefined),
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const createdTenant: TenantView = {
    id: 2,
    publicId: 'test-tenant',
    code: 'SOLUCIONES_TI',
    slug: 'soluciones-informaticas-ti',
    legalName: 'Soluciones Informáticas TI',
    displayName: 'Soluciones Informáticas TI',
    taxId: null,
    address: null,
    contactEmail: null,
    defaultCurrency: 'PEN',
    taxRate: 0.18,
    active: true,
    version: 0,
    primaryColor: '#E87835',
    secondaryColor: '#FFFFFF',
    accentColor: '#F3B247',
    pdfHeaderText: null,
    hasLogo: false,
    adminCount: 0,
  };

  function startNewTenant() {
    const fixture = TestBed.createComponent(PlatformPageComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/platform/tenants').flush([]);
    fixture.componentInstance.newTenant();
    fixture.detectChanges();
    return fixture;
  }

  it('loads the tenant directory and starts a new tenant without inventing legal data', () => {
    const fixture = TestBed.createComponent(PlatformPageComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/platform/tenants').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Empresas');
    const buttons = fixture.nativeElement.querySelectorAll(
      'button',
    ) as NodeListOf<HTMLButtonElement>;
    const newButton = Array.from(buttons).find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Nueva',
    ) as HTMLButtonElement;
    newButton.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nueva empresa');
    expect(fixture.nativeElement.querySelector('[formControlName="displayName"]').value).toBe(
      'Soluciones Informáticas TI',
    );
    expect(fixture.nativeElement.querySelector('[formControlName="slug"]').value).toBe(
      'soluciones-informaticas-ti',
    );
    expect(fixture.nativeElement.querySelector('[formControlName="legalName"]').value).toBe('');
    expect(fixture.nativeElement.querySelector('[formControlName="taxId"]').value).toBe('');
  });

  it('explains missing required fields instead of silently refusing submission', () => {
    const fixture = startNewTenant();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Revise los campos',
    );
    expect(fixture.nativeElement.querySelector('#tenant-code-error').textContent).toContain(
      'obligatorio',
    );
    expect(fixture.nativeElement.querySelector('#tenant-legalName-error').textContent).toContain(
      'obligatorio',
    );
    expect(
      fixture.nativeElement.querySelector('[formControlName="code"]').getAttribute('aria-invalid'),
    ).toBe('true');
    http.expectNone((request) => request.method === 'POST');
  });

  it('accepts a lowercase code and trimmed slug, then keeps the creation confirmation', () => {
    const fixture = startNewTenant();
    fixture.componentInstance.tenantForm.patchValue({
      code: ' soluciones_ti ',
      slug: ' SOLUCIONES-INFORMATICAS-TI ',
      legalName: ' Soluciones Informáticas TI ',
    });
    const taxInput = fixture.nativeElement.querySelector(
      '[formControlName="taxRate"]',
    ) as HTMLInputElement;
    taxInput.value = '0.18';
    taxInput.dispatchEvent(new Event('input'));
    fixture.componentInstance.saveTenant();
    fixture.componentInstance.saveTenant();

    const request = http.expectOne({ method: 'POST', url: '/api/v1/platform/tenants' });
    expect(request.request.body.code).toBe('SOLUCIONES_TI');
    expect(request.request.body.slug).toBe('soluciones-informaticas-ti');
    expect(request.request.body.legalName).toBe('Soluciones Informáticas TI');
    expect(request.request.body.taxRate).toBe(0.18);
    request.flush(createdTenant);
    http.expectOne({ method: 'GET', url: '/api/v1/platform/tenants' }).flush([createdTenant]);
    http.expectOne('/api/v1/platform/tenants/2/admins').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain(
      'Empresa creada',
    );
    expect(fixture.nativeElement.textContent).toContain('Administradores de Soluciones');
  });

  it('rejects blank legal names, invalid email and out-of-range tax without sending a request', () => {
    const fixture = startNewTenant();
    fixture.componentInstance.tenantForm.patchValue({
      code: 'SOLUCIONES_TI',
      legalName: '   ',
      contactEmail: 'sin-correo',
      taxRate: '18',
    });
    fixture.componentInstance.saveTenant();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#tenant-legalName-error').textContent).toContain(
      'obligatorio',
    );
    expect(fixture.nativeElement.querySelector('#tenant-contactEmail-error').textContent).toContain(
      'correo válido',
    );
    expect(fixture.nativeElement.querySelector('#tenant-taxRate-error').textContent).toContain(
      'entre 0 y 1',
    );
    http.expectNone((request) => request.method === 'POST');
  });

  it('keeps the form and explains a server rejection without claiming success', () => {
    const fixture = startNewTenant();
    fixture.componentInstance.tenantForm.patchValue({
      code: 'SOLUCIONES_TI',
      legalName: 'Soluciones Informáticas TI',
    });
    fixture.componentInstance.saveTenant();
    http
      .expectOne({ method: 'POST', url: '/api/v1/platform/tenants' })
      .flush(
        { detail: 'El código o slug ya está registrado' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'ya está registrado',
    );
    expect(fixture.componentInstance.tenantForm.controls.code.value).toBe('SOLUCIONES_TI');
    expect(fixture.componentInstance.saving()).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
  });

  it('explains invalid admin fields instead of silently refusing account creation', () => {
    const fixture = TestBed.createComponent(PlatformPageComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/platform/tenants').flush([createdTenant]);
    fixture.componentInstance.selectTenant(createdTenant);
    http.expectOne('/api/v1/platform/tenants/2/admins').flush([]);
    fixture.componentInstance.createOrLinkAdmin();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Revise el usuario',
    );
    expect(fixture.nativeElement.textContent).toContain('correo válido');
    http.expectNone((request) => request.method === 'POST');
  });

  it('keeps a new admin temporary password after the directory refresh until explicitly hidden', () => {
    const fixture = TestBed.createComponent(PlatformPageComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/platform/tenants').flush([createdTenant]);
    fixture.componentInstance.selectTenant(createdTenant);
    http.expectOne('/api/v1/platform/tenants/2/admins').flush([]);
    fixture.componentInstance.adminForm.setValue({ username: 'qa.admin', displayName: 'QA Admin' });
    fixture.componentInstance.createOrLinkAdmin();
    const admin = {
      workerId: 999,
      username: 'qa.admin',
      displayName: 'QA Admin',
      enabled: true,
      version: 0,
    };
    const syntheticPassword = 'SyntheticPasswordForTest';
    http.expectOne({ method: 'POST', url: '/api/v1/platform/tenants/2/admins' }).flush({
      account: admin,
      created: true,
      temporaryPassword: syntheticPassword,
    });
    http.expectOne('/api/v1/platform/tenants/2/admins').flush([admin]);
    http.expectOne('/api/v1/platform/tenants').flush([{ ...createdTenant, adminCount: 1 }]);
    http.expectOne('/api/v1/platform/tenants/2/admins').flush([admin]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.password-panel code')?.textContent).toBe(
      syntheticPassword,
    );
    fixture.componentInstance.clearTemporaryPassword();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.password-panel')).toBeNull();
  });

  it('clears the temporary password when manually selecting a company', () => {
    const fixture = TestBed.createComponent(PlatformPageComponent);
    http.expectOne('/api/v1/platform/tenants').flush([createdTenant]);
    fixture.componentInstance.temporaryPassword.set('SyntheticPasswordForTest');
    fixture.componentInstance.selectTenant(createdTenant);
    http.expectOne('/api/v1/platform/tenants/2/admins').flush([]);
    expect(fixture.componentInstance.temporaryPassword()).toBeNull();
  });
});
