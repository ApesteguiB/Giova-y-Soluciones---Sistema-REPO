import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  input,
  viewChild,
  viewChildren,
} from '@angular/core';
import { Brand } from '../core/catalog-api.service';

@Component({
  selector: 'app-brands-orbit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="orbit" #orbit [class.is-grid]="grid" aria-label="Marcas aliadas">
      <div class="orbit-center">
        <div class="gr-core">
          <span class="gr-pulse"></span>
          <span class="gr-pulse"></span>
          <span class="gr-pulse"></span>
          <div class="gr-disc">
            <img src="assets/monograma-gr.svg" alt="GR Giova Representaciones" />
          </div>
        </div>
      </div>
      @for (brand of brands(); track brand.id; let i = $index) {
        <div class="orbit-item" #orbitItem [attr.data-angle]="angle(i)">
          <div class="ol-inner" #inner [class.no-img]="!brand.logoUrl">
            @if (brand.logoUrl) {
              <img
                [src]="brand.logoUrl"
                [alt]="brand.name"
                loading="lazy"
                (error)="inner.classList.add('no-img')"
              />
            }
            <span class="txt" [style.color]="brand.displayColor || '#0A2342'">{{
              brand.name
            }}</span>
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
})
export class BrandsOrbitComponent implements AfterViewInit, OnDestroy {
  readonly brands = input<Brand[]>([]);

  protected grid = false;

  private readonly orbit = viewChild.required<ElementRef<HTMLElement>>('orbit');
  private readonly items = viewChildren<ElementRef<HTMLElement>>('orbitItem');

  private frame = 0;
  private autoRotation = 0;
  private scrollProgress = 0;
  private running = false;
  private reduceMotion = false;

  private readonly onScroll = () => this.readScroll();
  private readonly onResize = () => this.setMode();

  protected angle(index: number): number {
    const total = this.brands().length || 1;
    return Number(((index * 360) / total).toFixed(2));
  }

  ngAfterViewInit(): void {
    this.reduceMotion = window.matchMedia('(prefers-reduced-motion:reduce)').matches;
    window.addEventListener('scroll', this.onScroll, { passive: true });
    window.addEventListener('resize', this.onResize);
    this.readScroll();
    this.setMode();
  }

  ngOnDestroy(): void {
    window.removeEventListener('scroll', this.onScroll);
    window.removeEventListener('resize', this.onResize);
    this.running = false;
    if (this.frame) cancelAnimationFrame(this.frame);
  }

  private readScroll(): void {
    const rect = this.orbit().nativeElement.getBoundingClientRect();
    const viewport = window.innerHeight || 800;
    const center = rect.top + rect.height / 2;
    this.scrollProgress = Math.max(-1, Math.min(1, (viewport / 2 - center) / viewport));
  }

  private setMode(): void {
    if (window.innerWidth < 760) {
      this.grid = true;
      this.running = false;
      for (const item of this.items()) item.nativeElement.style.transform = '';
      return;
    }
    this.grid = false;
    if (!this.running) {
      this.running = true;
      this.frame = requestAnimationFrame(() => this.tick());
    }
  }

  private tick(): void {
    if (!this.running) return;
    if (!this.reduceMotion) this.autoRotation += 0.05;
    this.layout();
    this.frame = requestAnimationFrame(() => this.tick());
  }

  private layout(): void {
    const host = this.orbit().nativeElement;
    if (host.classList.contains('is-grid')) return;
    const progress = this.scrollProgress;
    const radiusX = Math.max(220, host.clientWidth / 2 - 96) + progress * 150;
    const radiusY = Math.max(180, host.clientHeight / 2 - 96) + progress * 110;
    const rotation = this.autoRotation + progress * 28;
    const scale = 1 - Math.abs(progress) * 0.05;
    for (const item of this.items()) {
      const element = item.nativeElement;
      const angle = ((Number(element.dataset['angle']) || 0) + rotation - 90) * (Math.PI / 180);
      const x = Math.cos(angle) * radiusX;
      const y = Math.sin(angle) * radiusY;
      element.style.transform = `translate(${x.toFixed(1)}px,${y.toFixed(1)}px) scale(${scale.toFixed(3)})`;
    }
    const layers = document.querySelectorAll<HTMLElement>('.brands-layer');
    layers.forEach((layer, index) => {
      const depth = (index + 1) * 0.5;
      layer.style.transform =
        `translate3d(-50%,calc(-50% + ${(progress * depth * 70).toFixed(1)}px),0)` +
        ` scale(${(1 + Math.abs(progress) * depth * 0.14).toFixed(3)})`;
    });
  }
}
