import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PublicSiteResponse, PublicSiteService, surfaceBase } from './public-site.service';

const SOLUCIONES: PublicSiteResponse = {
  tenantCode: 'SOLUCIONES_TI',
  templateKey: 'SOLUCIONES',
  siteTitle: 'Soluciones Informática TI | Infraestructura y soporte',
  brandName: 'Soluciones Informática TI',
  legalName: 'SOLUCIONES INFORMATICA TI E.I.R.L.',
  taxId: null,
  tagline: 'Infraestructura, redes y soporte informático',
  logoAsset: 'assets/soluciones-informaticas-ti.svg',
  uploadedLogo: false,
  monogramText: 'TI',
  phone: null,
  whatsappNumber: null,
  whatsappMessage: null,
  email: null,
  address: null,
  mapsUrl: null,
  hours: null,
  footerNote: null,
  facebookUrl: null,
  instagramUrl: null,
  linkedinUrl: null,
  claimsEnabled: true,
  catalogEnabled: true,
  colors: { primary: '#F26B21', secondary: '#1F2937', accent: '#F6B847' },
};

describe('PublicSiteService', () => {
  let service: PublicSiteService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PublicSiteService);
    http = TestBed.inject(HttpTestingController);
    document.documentElement.removeAttribute('data-public-theme');
  });

  afterEach(() => http.verify());

  it('loads the site of the company that owns the domain and marks the document theme', async () => {
    const loading = service.load();
    http.expectOne('/api/v1/catalog/site').flush(SOLUCIONES);
    await loading;

    expect(service.brandName()).toBe('Soluciones Informática TI');
    expect(service.template()).toBe('SOLUCIONES');
    expect(service.monogram()).toBe('TI');
    expect(service.logoSrc()).toBe('assets/soluciones-informaticas-ti.svg');
    expect(service.whatsappLink()).toBeNull();
    expect(document.documentElement.getAttribute('data-public-theme')).toBe('SOLUCIONES_TI');
    expect(document.title).toContain('Soluciones Informática TI');
  });

  it('keeps GIOVA on its own palette, with no theme attribute', async () => {
    const loading = service.load();
    http
      .expectOne('/api/v1/catalog/site')
      .flush({ ...SOLUCIONES, tenantCode: 'GIOVA', templateKey: 'GIOVA', brandName: 'Giova' });
    await loading;

    expect(document.documentElement.hasAttribute('data-public-theme')).toBe(false);
  });

  it('falls back to the GIOVA site when the endpoint is unavailable', async () => {
    const loading = service.load();
    http.expectOne('/api/v1/catalog/site').flush('', { status: 404, statusText: 'Not Found' });
    await loading;

    expect(service.loaded()).toBe(false);
    expect(service.template()).toBe('GIOVA');
    expect(service.brandName()).toBe('Giova Representaciones');
    expect(service.whatsappLink()).toContain('wa.me/51968359290');
  });

  it('serves the uploaded logo through the public endpoint and falls back if it fails', async () => {
    const loading = service.load();
    http.expectOne('/api/v1/catalog/site').flush({ ...SOLUCIONES, uploadedLogo: true });
    await loading;

    expect(service.logoSrc()).toBe('/api/v1/catalog/site/logo');
    service.logoLoadFailed();
    expect(service.logoSrc()).toBe('assets/soluciones-informaticas-ti.svg');
  });

  it('uses a neutral dark surface when the company picked a light secondary colour', () => {
    expect(surfaceBase('#1F2937')).toBe('#1F2937');
    expect(surfaceBase('#FFFFFF')).toBe('#1F2937');
    expect(surfaceBase('#F3B247')).toBe('#1F2937');
  });

  it('builds a per-product WhatsApp message only when the company has a number', async () => {
    const loading = service.load();
    http.expectOne('/api/v1/catalog/site').flush({ ...SOLUCIONES, whatsappNumber: '51999888777' });
    await loading;

    const link = service.productWhatsappLink('Switch 24 puertos', 'USW-24-POE');
    expect(link).toContain('wa.me/51999888777');
    expect(link).toContain(encodeURIComponent('USW-24-POE'));
  });
});
