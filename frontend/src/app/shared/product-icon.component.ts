import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * SVG genérico por categoría, portado de GIOVA_ICONS en js/products-data.js.
 * Se usa como imagen del producto cuando no hay foto o cuando la foto falla.
 */
@Component({
  selector: 'app-product-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (iconKey()) {
      @case ('toner-hp') {
        <svg viewBox="0 0 160 110" aria-hidden="true">
          <rect x="22" y="46" width="116" height="40" rx="9" fill="var(--navy-800)" />
          <rect x="40" y="32" width="64" height="18" rx="3" fill="#0096D6" />
          <rect x="38" y="58" width="60" height="18" rx="4" fill="var(--navy-700)" />
          <circle cx="118" cy="66" r="8" fill="var(--cyan-400)" />
          <text x="46" y="46" font-family="Arial" font-size="11" font-weight="bold" fill="#fff">
            HP
          </text>
        </svg>
      }
      @case ('toner-kyocera') {
        <svg viewBox="0 0 160 110" aria-hidden="true">
          <rect x="20" y="44" width="120" height="42" rx="9" fill="var(--navy-900)" />
          <rect x="34" y="30" width="72" height="18" rx="3" fill="#E4002B" />
          <rect x="34" y="56" width="66" height="20" rx="4" fill="var(--navy-700)" />
          <circle cx="120" cy="66" r="8" fill="var(--teal-500)" />
          <text x="38" y="44" font-family="Arial" font-size="9" font-weight="bold" fill="#fff">
            KYOCERA
          </text>
        </svg>
      }
      @case ('impresoras') {
        <svg viewBox="0 0 160 110" aria-hidden="true">
          <rect x="40" y="20" width="80" height="24" rx="3" fill="var(--navy-800)" />
          <rect x="48" y="26" width="64" height="12" rx="2" fill="var(--teal-500)" />
          <rect x="28" y="40" width="104" height="38" rx="7" fill="var(--navy-700)" />
          <circle cx="116" cy="52" r="4" fill="var(--cyan-400)" />
          <rect x="40" y="48" width="34" height="6" rx="3" fill="var(--cyan-400)" />
          <rect
            x="44"
            y="70"
            width="72"
            height="26"
            rx="2"
            fill="#fff"
            stroke="var(--ice-200)"
            stroke-width="1.5"
          />
          <rect
            x="52"
            y="78"
            width="40"
            height="3.5"
            rx="1.75"
            fill="var(--navy-800)"
            opacity=".35"
          />
          <rect
            x="52"
            y="86"
            width="56"
            height="3.5"
            rx="1.75"
            fill="var(--navy-800)"
            opacity=".25"
          />
        </svg>
      }
      @case ('repuestos') {
        <svg viewBox="0 0 160 110" aria-hidden="true">
          <rect x="44" y="28" width="72" height="54" rx="5" fill="var(--navy-800)" />
          <rect x="56" y="40" width="48" height="30" rx="3" fill="var(--teal-500)" />
          <g stroke="var(--blue-600)" stroke-width="4" stroke-linecap="round">
            <path d="M56 18v8M72 18v8M88 18v8M104 18v8M56 82v8M72 82v8M88 82v8M104 82v8" />
          </g>
        </svg>
      }
      @case ('redes') {
        <svg viewBox="0 0 160 110" aria-hidden="true">
          <rect x="28" y="58" width="104" height="24" rx="5" fill="var(--navy-700)" />
          <circle cx="44" cy="70" r="4" fill="var(--cyan-400)" />
          <circle cx="58" cy="70" r="4" fill="var(--cyan-400)" />
          <circle cx="72" cy="70" r="4" fill="var(--cyan-400)" />
          <path
            d="M50 40c12-12 38-12 50 0M60 30c8-8 22-8 30 0"
            stroke="var(--teal-500)"
            stroke-width="5"
            fill="none"
            stroke-linecap="round"
          />
        </svg>
      }
      @case ('seguridad') {
        <svg viewBox="0 0 160 110" aria-hidden="true">
          <rect x="30" y="36" width="56" height="30" rx="9" fill="var(--navy-800)" />
          <circle cx="56" cy="51" r="10" fill="var(--cyan-400)" />
          <path d="M86 42l30-12v40l-30-12z" fill="var(--teal-500)" />
          <rect x="48" y="66" width="16" height="22" fill="var(--blue-600)" />
          <rect x="34" y="88" width="44" height="7" rx="3.5" fill="var(--navy-700)" />
        </svg>
      }
      @case ('servicios') {
        <svg viewBox="0 0 160 110" aria-hidden="true">
          <path
            d="M86 26a20 20 0 00-25 25L40 72a8 8 0 0011 11l21-21a20 20 0 0025-25l-12 12-12-3-3-12z"
            fill="var(--teal-500)"
          />
          <circle cx="98" cy="84" r="13" fill="var(--navy-800)" />
          <circle cx="98" cy="84" r="5" fill="var(--cyan-400)" />
        </svg>
      }
      @default {
        <svg viewBox="0 0 160 110" aria-hidden="true">
          <rect x="34" y="22" width="92" height="58" rx="6" fill="var(--navy-700)" />
          <rect x="40" y="28" width="80" height="46" rx="2" fill="var(--cyan-400)" />
          <rect x="50" y="38" width="44" height="7" rx="3.5" fill="#fff" opacity=".9" />
          <rect x="50" y="52" width="58" height="5" rx="2.5" fill="var(--navy-800)" opacity=".4" />
          <path d="M22 80h116l-9 14H31z" fill="var(--navy-800)" />
        </svg>
      }
    }
  `,
  styles: `
    :host {
      display: grid;
      width: 100%;
      height: 100%;
      place-items: center;
    }
    svg {
      width: 100%;
      height: 100%;
      max-height: 150px;
    }
  `,
})
export class ProductIconComponent {
  readonly iconKey = input<string | null | undefined>('equipos');
}
