import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, firstValueFrom, of } from 'rxjs';
import { contrastColor, contrastOnWhite } from './tenant-branding.service';

export type SiteTemplate = 'GIOVA' | 'SOLUCIONES' | 'GENERICO';

export interface PublicSiteColors {
  primary: string;
  secondary: string;
  accent: string;
}

export interface PublicSiteResponse {
  tenantCode: string;
  templateKey: SiteTemplate;
  siteTitle: string;
  brandName: string;
  legalName: string;
  taxId: string | null;
  tagline: string | null;
  logoAsset: string | null;
  uploadedLogo: boolean;
  monogramText: string | null;
  phone: string | null;
  whatsappNumber: string | null;
  whatsappMessage: string | null;
  email: string | null;
  address: string | null;
  mapsUrl: string | null;
  hours: string | null;
  footerNote: string | null;
  facebookUrl: string | null;
  instagramUrl: string | null;
  linkedinUrl: string | null;
  claimsEnabled: boolean;
  catalogEnabled: boolean;
  colors: PublicSiteColors;
}

export interface PublicSiteTheme {
  primary: string;
  primaryContrast: string;
  primaryText: string;
  secondary: string;
  secondaryContrast: string;
  accent: string;
  accentContrast: string;
}

/**
 * Sitio que se usa si el backend todavía no responde (catálogo apagado, API
 * caída): es el de GIOVA, para que la portada nunca quede en blanco.
 */
const FALLBACK_SITE: PublicSiteResponse = {
  tenantCode: 'GIOVA',
  templateKey: 'GIOVA',
  siteTitle: 'Giova Representaciones | Tecnología',
  brandName: 'Giova Representaciones',
  legalName: 'GIOVA REPRESENTACIONES S.A.C.',
  taxId: null,
  tagline:
    'Equipos informáticos, impresoras y tóners originales HP y Kyocera para el sector público y privado',
  logoAsset: 'assets/logo.svg',
  uploadedLogo: false,
  monogramText: 'GR',
  phone: '(01) 903 9891 · 968 359 290',
  whatsappNumber: '51968359290',
  whatsappMessage:
    'Hola, deseo recibir información sobre los productos y servicios tecnológicos que ofrecen.',
  email: 'ventas@giova.pe',
  address: 'Calle Pedro Donofrio 171 Int. 302, Bellavista, Callao',
  mapsUrl: 'https://maps.app.goo.gl/BgsorZ1sQWj4tub56',
  hours: 'Lunes a viernes de 9:00 a 18:00',
  footerNote: 'Proveedor habilitado en OSCE | Empresa Homologada',
  facebookUrl: null,
  instagramUrl: null,
  linkedinUrl: null,
  claimsEnabled: true,
  catalogEnabled: true,
  colors: { primary: '#0a2342', secondary: '#14a3a8', accent: '#2ec8dd' },
};

/**
 * Empresa forzada por la URL (?empresa=giova | ?empresa=soluciones-ti). Se lee
 * una sola vez al cargar y el backend solo la acepta desde localhost, así que
 * sirve para demostrar las dos empresas sin configurar dominios.
 */
const TENANT_OVERRIDE = readTenantOverride();

function readTenantOverride(): string | null {
  if (typeof window === 'undefined') return null;
  const value = new URLSearchParams(window.location.search).get('empresa');
  return value && /^[A-Za-z0-9_-]{1,40}$/.test(value) ? value : null;
}

export function publicTenantOverride(): string | null {
  return TENANT_OVERRIDE;
}

@Injectable({ providedIn: 'root' })
export class PublicSiteService {
  private readonly http = inject(HttpClient);
  private readonly response = signal<PublicSiteResponse>(FALLBACK_SITE);
  readonly loaded = signal(false);

  readonly site = computed(() => this.response());
  readonly template = computed<SiteTemplate>(() => this.response().templateKey ?? 'GIOVA');
  readonly brandName = computed(() => this.response().brandName);
  readonly legalName = computed(() => this.response().legalName);
  readonly claimsEnabled = computed(() => this.response().claimsEnabled !== false);
  readonly logoFailed = signal(false);

  readonly logoSrc = computed(() => {
    const site = this.response();
    if (site.uploadedLogo && !this.logoFailed()) return '/api/v1/catalog/site/logo';
    return site.logoAsset ?? 'assets/logo.svg';
  });

  readonly monogram = computed(() => {
    const site = this.response();
    if (site.monogramText) return site.monogramText;
    return site.brandName
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((word) => word[0]?.toLocaleUpperCase() ?? '')
      .join('');
  });

  readonly theme = computed<PublicSiteTheme>(() => {
    const colors = this.response().colors ?? FALLBACK_SITE.colors;
    const primary = safeColor(colors.primary, FALLBACK_SITE.colors.primary);
    const secondary = safeColor(colors.secondary, FALLBACK_SITE.colors.secondary);
    const accent = safeColor(colors.accent, FALLBACK_SITE.colors.accent);
    return {
      primary,
      primaryContrast: contrastColor(primary),
      primaryText: contrastOnWhite(primary),
      secondary,
      secondaryContrast: contrastColor(secondary),
      accent,
      accentContrast: contrastColor(accent),
    };
  });

  readonly whatsappLink = computed(() => {
    const site = this.response();
    if (!site.whatsappNumber) return null;
    const text = site.whatsappMessage ?? 'Hola, deseo recibir información sobre sus servicios.';
    return `https://wa.me/${site.whatsappNumber}?text=${encodeURIComponent(text)}`;
  });

  /** Enlace de WhatsApp para un producto concreto del catálogo. */
  productWhatsappLink(name: string, sku?: string | null): string | null {
    const number = this.response().whatsappNumber;
    if (!number) return null;
    const text =
      `Hola, estoy interesado en el producto: ${name}` +
      (sku ? ` (SKU: ${sku})` : '') +
      '. Deseo recibir más información y cotización.';
    return `https://wa.me/${number}?text=${encodeURIComponent(text)}`;
  }

  async load(): Promise<void> {
    const site = await firstValueFrom(
      this.http
        .get<PublicSiteResponse>('/api/v1/catalog/site')
        .pipe(catchError(() => of(null as PublicSiteResponse | null))),
    );
    if (site && typeof site.brandName === 'string' && site.brandName.trim()) {
      this.response.set({ ...FALLBACK_SITE, ...site });
      this.loaded.set(true);
    }
    this.applyDocumentChrome();
  }

  logoLoadFailed(): void {
    this.logoFailed.set(true);
  }

  /**
   * Pinta la paleta de la empresa en el documento. GIOVA no lleva el atributo
   * data-public-theme, así que su portada queda exactamente igual que antes.
   */
  private applyTheme(): void {
    const root = document.documentElement;
    const theme = this.theme();
    const entries: Record<string, string> = {
      '--tenant-primary': theme.primary,
      '--tenant-primary-contrast': theme.primaryContrast,
      '--tenant-primary-text': theme.primaryText,
      '--tenant-secondary': theme.secondary,
      '--tenant-secondary-contrast': theme.secondaryContrast,
      '--tenant-accent': theme.accent,
      '--tenant-accent-contrast': theme.accentContrast,
      // Superficies oscuras de la portada: se parte del color secundario, que
      // suele ser el oscuro de la marca, y se oscurece hasta ser legible. Si la
      // empresa eligió un secundario claro (blanco, por ejemplo), oscurecerlo
      // daría un gris apagado: en ese caso se usa un gris azulado neutro y la
      // marca se nota en los acentos.
      '--public-surface': darkenTo(surfaceBase(theme.secondary), 0.045),
      '--public-surface-deep': darkenTo(surfaceBase(theme.secondary), 0.011),
    };
    for (const [name, value] of Object.entries(entries)) {
      root.style.setProperty(name, value);
    }
    if (this.template() === 'GIOVA') {
      root.removeAttribute('data-public-theme');
    } else {
      root.setAttribute('data-public-theme', this.response().tenantCode);
    }
  }

  private applyDocumentChrome(): void {
    if (typeof document === 'undefined') return;
    const site = this.response();
    this.applyTheme();
    document.title = site.siteTitle;
    const icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (icon) {
      icon.href = site.uploadedLogo ? '/api/v1/catalog/site/logo' : (site.logoAsset ?? icon.href);
      icon.type = site.uploadedLogo ? 'image/png' : icon.type;
    }
    const description = document.querySelector<HTMLMetaElement>('meta[name="description"]');
    if (description && site.tagline) {
      description.content = `${site.legalName} — ${site.tagline}`;
    }
  }
}

function safeColor(value: string | undefined, fallback: string): string {
  return value && /^#[\da-fA-F]{6}$/.test(value) ? value : fallback;
}

/** Gris azulado neutro para empresas cuyo color secundario no sirve de fondo. */
const NEUTRAL_SURFACE = '#1F2937';

/** Un secundario claro no puede ser la base de un fondo oscuro. */
export function surfaceBase(secondary: string): string {
  const channels = [1, 3, 5].map((offset) =>
    Number.parseInt(secondary.slice(offset, offset + 2), 16),
  );
  return luminance(channels) <= 0.25 ? secondary : NEUTRAL_SURFACE;
}

/**
 * Oscurece un color hasta que su luminancia relativa baja del umbral, para que
 * las superficies oscuras de la portada mantengan el texto blanco legible
 * aunque la empresa haya elegido colores claros.
 */
function darkenTo(hex: string, maxLuminance: number): string {
  const channels = [1, 3, 5].map((offset) => Number.parseInt(hex.slice(offset, offset + 2), 16));
  for (let step = 0; step <= 100; step++) {
    const factor = 1 - step / 100;
    const candidate = channels.map((channel) => Math.round(channel * factor));
    if (luminance(candidate) <= maxLuminance) {
      return (
        '#' + candidate.map((channel) => channel.toString(16).padStart(2, '0')).join('')
      ).toUpperCase();
    }
  }
  return '#000000';
}

function luminance(channels: number[]): number {
  const linear = channels.map((channel) => {
    const value = channel / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722;
}
