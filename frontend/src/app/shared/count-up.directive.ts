import { Directive, ElementRef, OnDestroy, OnInit, inject, input } from '@angular/core';

/** Contadores animados del hero, equivalente a [data-count] de js/main.js. */
@Directive({
  selector: '[appCountUp]',
  standalone: true,
})
export class CountUpDirective implements OnInit, OnDestroy {
  readonly appCountUp = input.required<number>();

  private readonly host = inject(ElementRef<HTMLElement>);
  private observer?: IntersectionObserver;
  private frame = 0;

  ngOnInit(): void {
    const element = this.host.nativeElement as HTMLElement;
    element.textContent = '0';
    if (typeof IntersectionObserver === 'undefined') {
      this.animate(element);
      return;
    }
    this.observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          this.animate(element);
          this.observer?.unobserve(element);
        }
      },
      { threshold: 0.4 },
    );
    this.observer.observe(element);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
    if (this.frame) cancelAnimationFrame(this.frame);
  }

  private animate(element: HTMLElement): void {
    const target = this.appCountUp();
    const suffix = target >= 100 ? '' : '+';
    const duration = 1500;
    const start = performance.now();
    const tick = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      element.textContent = Math.round(target * eased) + (progress === 1 ? suffix : '');
      if (progress < 1) this.frame = requestAnimationFrame(tick);
    };
    this.frame = requestAnimationFrame(tick);
  }
}
