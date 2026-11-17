import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { map, Observable, tap } from 'rxjs';

export interface TenantBrandingResponse {
  tenantId: number;
  displayName: string;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
  pdfHeaderText: string | null;
  hasLogo: boolean;
}

export interface TenantBranding extends TenantBrandingResponse {
  primaryContrast: string;
  primaryTextColor: string;
  secondaryContrast: string;
  accentContrast: string;
}

export function resolveTenantBranding(response: TenantBrandingResponse): TenantBranding {
  if (!Number.isSafeInteger(response.tenantId) || response.tenantId <= 0) {
    throw new Error('La empresa de la marca no es válida.');
  }

  const displayName = response.displayName?.trim();
  if (!displayName || typeof response.hasLogo !== 'boolean') {
    throw new Error('La respuesta de marca está incompleta.');
  }

  const primaryColor = normalizeHexColor(response.primaryColor);
  const secondaryColor = normalizeHexColor(response.secondaryColor);
  const accentColor = normalizeHexColor(response.accentColor);

  return {
    ...response,
    displayName,
    primaryColor,
    secondaryColor,
    accentColor,
    primaryContrast: contrastColor(primaryColor),
    primaryTextColor: contrastOnWhite(primaryColor),
    secondaryContrast: contrastColor(secondaryColor),
    accentContrast: contrastColor(accentColor),
  };
}

@Injectable({ providedIn: 'root' })
export class TenantBrandingService {
  private readonly http = inject(HttpClient);
  readonly branding = signal<TenantBranding | null>(null);
  readonly logoFailed = signal(false);
  readonly logoVisible = computed(() => !!this.branding()?.hasLogo && !this.logoFailed());
  readonly monogram = computed(() => {
    const name = this.branding()?.displayName ?? 'GIOVA';
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((word) => word[0]?.toLocaleUpperCase())
      .join('');
  });
  readonly staticLogo = computed(() => {
    const name = (this.branding()?.displayName ?? '').toLowerCase();
    if (name.includes('solucion')) return '/brand/soluciones.png';
    if (name.includes('giova')) return '/brand/giova.png';
    return null;
  });
  readonly logoUrl = '/api/v1/tenant/branding/logo';

  load(): Observable<TenantBranding> {
    this.clear();
    return this.http.get<TenantBrandingResponse>('/api/v1/tenant/branding').pipe(
      map(resolveTenantBranding),
      tap((branding) => {
        this.logoFailed.set(false);
        this.branding.set(branding);
      }),
    );
  }

  clear(): void {
    this.logoFailed.set(false);
    this.branding.set(null);
  }

  logoLoadFailed(): void {
    this.logoFailed.set(true);
  }
}

export function normalizeHexColor(value: string): string {
  if (!/^#[\da-f]{6}$/i.test(value)) {
    throw new Error('La marca incluye un color inválido.');
  }
  return value.toUpperCase();
}

export function contrastColor(hex: string): string {
  const blackContrast = contrastRatio('#000000', hex);
  const whiteContrast = contrastRatio('#FFFFFF', hex);
  return blackContrast >= whiteContrast ? '#000000' : '#FFFFFF';
}

export function contrastOnWhite(hex: string): string {
  // Reserve contrast for the lightly tinted operational surfaces as well as white.
  const minimumContrast = 5.5;
  if (contrastRatio(hex, '#FFFFFF') >= minimumContrast) return hex;
  const channels = [1, 3, 5].map((offset) => Number.parseInt(hex.slice(offset, offset + 2), 16));
  for (let step = 1; step <= 100; step++) {
    const darker =
      '#' +
      channels
        .map((channel) =>
          Math.round(channel * (1 - step / 100))
            .toString(16)
            .padStart(2, '0'),
        )
        .join('')
        .toUpperCase();
    if (contrastRatio(darker, '#FFFFFF') >= minimumContrast) return darker;
  }
  return '#000000';
}

function contrastRatio(foreground: string, background: string): number {
  const first = relativeLuminance(foreground);
  const second = relativeLuminance(background);
  const lighter = Math.max(first, second);
  const darker = Math.min(first, second);
  return (lighter + 0.05) / (darker + 0.05);
}

function relativeLuminance(hex: string): number {
  const channels = [1, 3, 5].map(
    (offset) => Number.parseInt(hex.slice(offset, offset + 2), 16) / 255,
  );
  const linearChannels = channels.map((channel) =>
    channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4,
  );
  return linearChannels[0] * 0.2126 + linearChannels[1] * 0.7152 + linearChannels[2] * 0.0722;
}
