import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TenantBrandingService, resolveTenantBranding } from './tenant-branding.service';

describe('TenantBrandingService', () => {
  let service: TenantBrandingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TenantBrandingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the active tenant branding and derives readable foreground colors', () => {
    let receivedName = '';
    service.load().subscribe((branding) => (receivedName = branding.displayName));

    const request = http.expectOne('/api/v1/tenant/branding');
    expect(request.request.method).toBe('GET');
    request.flush({
      tenantId: 2,
      displayName: 'Soluciones Informáticas TI',
      primaryColor: '#F0781E',
      secondaryColor: '#FFFFFF',
      accentColor: '#17202A',
      pdfHeaderText: null,
      hasLogo: true,
    });

    expect(receivedName).toBe('Soluciones Informáticas TI');
    expect(service.branding()?.primaryColor).toBe('#F0781E');
    expect(service.branding()?.primaryContrast).toBe('#000000');
    expect(service.branding()?.primaryTextColor).not.toBe('#0E1B2C');
    expect(contrastWithWhite(service.branding()!.primaryTextColor)).toBeGreaterThanOrEqual(4.5);
    expect(service.branding()?.secondaryContrast).toBe('#000000');
    expect(service.branding()?.accentContrast).toBe('#FFFFFF');
    expect(service.logoVisible()).toBe(true);
    expect(service.logoUrl).toBe('/api/v1/tenant/branding/logo');
  });

  it('rejects malformed colors and clears the previous company brand', () => {
    service.branding.set(
      resolveTenantBranding({
        tenantId: 1,
        displayName: 'GIOVA',
        primaryColor: '#0A2342',
        secondaryColor: '#14A3A8',
        accentColor: '#2EC8DD',
        pdfHeaderText: null,
        hasLogo: false,
      }),
    );

    let failed = false;
    service.load().subscribe({ error: () => (failed = true) });
    http.expectOne('/api/v1/tenant/branding').flush({
      tenantId: 2,
      displayName: 'Empresa sintética',
      primaryColor: 'url(javascript:alert(1))',
      secondaryColor: '#FFFFFF',
      accentColor: '#17202A',
      pdfHeaderText: null,
      hasLogo: false,
    });

    expect(failed).toBe(true);
    expect(service.branding()).toBeNull();
  });

  it('clears cached branding and provides a monogram when the logo fails', () => {
    service.branding.set(
      resolveTenantBranding({
        tenantId: 2,
        displayName: 'Soluciones Informáticas TI',
        primaryColor: '#F0781E',
        secondaryColor: '#FFFFFF',
        accentColor: '#17202A',
        pdfHeaderText: null,
        hasLogo: true,
      }),
    );
    service.logoLoadFailed();

    expect(service.logoVisible()).toBe(false);
    expect(service.monogram()).toBe('SI');
    service.clear();
    expect(service.branding()).toBeNull();
    expect(service.logoFailed()).toBe(false);
    expect(service.monogram()).toBe('G');
  });

  it('derives readable brand-colored text for light, saturated and dark primary colors', () => {
    for (const primaryColor of ['#E87835', '#FFFFFF', '#F3B247', '#00FF00', '#0A2342', '#000000']) {
      const branding = resolveTenantBranding({
        tenantId: 2,
        displayName: 'Empresa',
        primaryColor,
        secondaryColor: '#FFFFFF',
        accentColor: '#F3B247',
        pdfHeaderText: null,
        hasLogo: false,
      });
      expect(contrastWithWhite(branding.primaryTextColor)).toBeGreaterThanOrEqual(4.5);
      if (primaryColor === '#E87835') {
        const channels = [1, 3, 5].map((offset) =>
          parseInt(branding.primaryTextColor.slice(offset, offset + 2), 16),
        );
        expect(channels[0]).toBeGreaterThan(channels[1]);
        expect(channels[1]).toBeGreaterThan(channels[2]);
      }
    }
  });

  it('replaces the previous tenant palette when loading another company', () => {
    const first = {
      tenantId: 2,
      displayName: 'TI',
      primaryColor: '#E87835',
      secondaryColor: '#FFFFFF',
      accentColor: '#F3B247',
      pdfHeaderText: null,
      hasLogo: false,
    };
    service.load().subscribe();
    http.expectOne('/api/v1/tenant/branding').flush(first);
    service.load().subscribe();
    expect(service.branding()).toBeNull();
    http.expectOne('/api/v1/tenant/branding').flush({
      ...first,
      tenantId: 1,
      displayName: 'GIOVA',
      primaryColor: '#0A2342',
      secondaryColor: '#14A3A8',
      accentColor: '#2EC8DD',
    });
    expect(service.branding()?.primaryColor).toBe('#0A2342');
    expect(service.branding()?.primaryTextColor).toBe('#0A2342');
    expect(service.branding()?.accentColor).toBe('#2EC8DD');
  });
});

function contrastWithWhite(hex: string): number {
  const linear = [1, 3, 5].map((offset) => {
    const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return 1.05 / (0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2] + 0.05);
}
