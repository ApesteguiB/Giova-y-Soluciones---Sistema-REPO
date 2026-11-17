import { Directive, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';

/**
 * Reveal on scroll con secuencia entre hermanos, equivalente al bloque
 * "Reveal on scroll con secuencia (stagger)" de js/main.js.
 */
@Directive({
  selector: '[appReveal]',
  standalone: true,
  host: { class: 'reveal' },
})
export class RevealDirective implements OnInit, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);
  private observer?: IntersectionObserver;

  ngOnInit(): void {
    const element = this.host.nativeElement as HTMLElement;
    if (typeof IntersectionObserver === 'undefined') {
      element.classList.add('in');
      return;
    }
    this.observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const siblings = Array.from(element.parentElement?.children ?? []).filter((child) =>
            child.classList.contains('reveal'),
          );
          const index = siblings.indexOf(element);
          element.style.transitionDelay = `${index > 0 ? Math.min(index, 6) * 90 : 0}ms`;
          element.classList.add('in');
          this.observer?.unobserve(element);
        }
      },
      { threshold: 0.12 },
    );
    this.observer.observe(element);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }
}
