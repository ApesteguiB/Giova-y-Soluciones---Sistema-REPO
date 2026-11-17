import {
  AfterViewInit,
  Component,
  computed,
  ElementRef,
  inject,
  OnDestroy,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, of } from 'rxjs';
import { CatalogApiService, Category } from '../core/catalog-api.service';
import { PublicSiteService } from '../core/public-site.service';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './public-layout.component.html',
  styleUrl: './public-layout.component.css',
})
export class PublicLayoutComponent implements OnInit, AfterViewInit, OnDestroy {
  readonly site = inject(PublicSiteService);
  private readonly catalog = inject(CatalogApiService);
  readonly year = new Date().getFullYear();
  readonly categories = signal<Category[]>([]);
  readonly isGiova = computed(() => this.site.template() === 'GIOVA');
  readonly footerIntro = computed(
    () => this.site.site().tagline ?? `${this.site.brandName()} — tecnología y servicios.`,
  );
  readonly menuOpen = signal(false);
  readonly splashHidden = signal(false);
  readonly splashGone = signal(false);
  readonly pointerFine = signal(false);

  private readonly cursor = viewChild<ElementRef<HTMLElement>>('grCursor');
  private readonly dot = viewChild<ElementRef<HTMLElement>>('grDot');

  private frame = 0;
  private timers: ReturnType<typeof setTimeout>[] = [];
  private mouseX = 0;
  private mouseY = 0;
  private cursorX = 0;
  private cursorY = 0;

  private static readonly HOVER_SELECTOR =
    'a, button, .btn, .cat-card, .show-card, .prod-card, .brand-pill, .filter-btn, .nav-cta,' +
    ' input, textarea, select, .info-card, [data-cursor]';

  private readonly onMouseMove = (event: MouseEvent) => {
    this.mouseX = event.clientX;
    this.mouseY = event.clientY;
    const dot = this.dot()?.nativeElement;
    if (!dot) return;
    dot.style.left = `${this.mouseX}px`;
    dot.style.top = `${this.mouseY}px`;
    dot.classList.add('show');
  };

  private readonly onMouseOver = (event: MouseEvent) => {
    const target = event.target as HTMLElement | null;
    if (target?.closest(PublicLayoutComponent.HOVER_SELECTOR)) {
      this.cursor()?.nativeElement.classList.add('show');
    }
  };

  private readonly onMouseOut = (event: MouseEvent) => {
    const target = event.target as HTMLElement | null;
    const related = event.relatedTarget as HTMLElement | null;
    if (
      target?.closest(PublicLayoutComponent.HOVER_SELECTOR) &&
      !related?.closest?.(PublicLayoutComponent.HOVER_SELECTOR)
    ) {
      this.cursor()?.nativeElement.classList.remove('show');
    }
  };

  private readonly onMouseLeave = () => {
    this.cursor()?.nativeElement.classList.remove('show');
    this.dot()?.nativeElement.classList.remove('show');
  };

  ngOnInit(): void {
    // Las categorías del pie salen del catálogo de la empresa dueña del
    // dominio; si la API falla, la columna simplemente no se muestra.
    this.catalog
      .categories()
      .pipe(catchError(() => of([] as Category[])))
      .subscribe((categories) => this.categories.set(categories.slice(0, 4)));
    this.timers.push(setTimeout(() => this.splashHidden.set(true), 900));
    this.timers.push(setTimeout(() => this.splashGone.set(true), 1600));
    const fine =
      !window.matchMedia('(hover:none),(pointer:coarse)').matches &&
      !window.matchMedia('(prefers-reduced-motion:reduce)').matches;
    this.pointerFine.set(fine);
  }

  ngAfterViewInit(): void {
    if (!this.pointerFine()) return;
    document.addEventListener('mousemove', this.onMouseMove);
    document.addEventListener('mouseover', this.onMouseOver);
    document.addEventListener('mouseout', this.onMouseOut);
    document.addEventListener('mouseleave', this.onMouseLeave);
    const loop = () => {
      this.cursorX += (this.mouseX - this.cursorX) * 0.18;
      this.cursorY += (this.mouseY - this.cursorY) * 0.18;
      const cursor = this.cursor()?.nativeElement;
      if (cursor) {
        cursor.style.left = `${this.cursorX}px`;
        cursor.style.top = `${this.cursorY}px`;
      }
      this.frame = requestAnimationFrame(loop);
    };
    this.frame = requestAnimationFrame(loop);
  }

  ngOnDestroy(): void {
    document.removeEventListener('mousemove', this.onMouseMove);
    document.removeEventListener('mouseover', this.onMouseOver);
    document.removeEventListener('mouseout', this.onMouseOut);
    document.removeEventListener('mouseleave', this.onMouseLeave);
    if (this.frame) cancelAnimationFrame(this.frame);
    for (const timer of this.timers) clearTimeout(timer);
    this.timers = [];
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }
}
