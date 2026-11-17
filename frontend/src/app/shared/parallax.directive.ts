import { Directive, ElementRef, OnDestroy, OnInit, inject, input } from '@angular/core';

/** Parallax suave al hacer scroll, equivalente a [data-parallax] de js/main.js. */
@Directive({
  selector: '[appParallax]',
  standalone: true,
})
export class ParallaxDirective implements OnInit, OnDestroy {
  readonly appParallax = input(0.2);

  private readonly host = inject(ElementRef<HTMLElement>);
  private ticking = false;
  private readonly onScroll = () => {
    if (this.ticking) return;
    this.ticking = true;
    requestAnimationFrame(() => {
      const speed = this.appParallax() || 0.2;
      (this.host.nativeElement as HTMLElement).style.transform =
        `translate3d(0,${window.pageYOffset * speed}px,0)`;
      this.ticking = false;
    });
  };

  ngOnInit(): void {
    if (window.matchMedia('(prefers-reduced-motion:reduce)').matches) return;
    window.addEventListener('scroll', this.onScroll, { passive: true });
  }

  ngOnDestroy(): void {
    window.removeEventListener('scroll', this.onScroll);
  }
}
