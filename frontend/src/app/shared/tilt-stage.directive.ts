import { Directive, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';

/**
 * Mouse tracking del hero: mueve los hijos con [data-depth],
 * equivalente al bloque "Mouse tracking (hero)" de js/main.js.
 */
@Directive({
  selector: '[appTiltStage]',
  standalone: true,
})
export class TiltStageDirective implements OnInit, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);
  private frame = 0;
  private targetX = 0;
  private targetY = 0;
  private currentX = 0;
  private currentY = 0;

  private readonly onMove = (event: MouseEvent) => {
    const rect = (this.host.nativeElement as HTMLElement).getBoundingClientRect();
    this.targetX = (event.clientX - rect.left) / rect.width - 0.5;
    this.targetY = (event.clientY - rect.top) / rect.height - 0.5;
  };

  private readonly onLeave = () => {
    this.targetX = 0;
    this.targetY = 0;
  };

  ngOnInit(): void {
    if (window.matchMedia('(hover:none),(pointer:coarse)').matches) return;
    if (window.matchMedia('(prefers-reduced-motion:reduce)').matches) return;
    const stage = this.host.nativeElement as HTMLElement;
    stage.addEventListener('mousemove', this.onMove);
    stage.addEventListener('mouseleave', this.onLeave);
    const items = Array.from(stage.querySelectorAll<HTMLElement>('[data-depth]'));
    const loop = () => {
      this.currentX += (this.targetX - this.currentX) * 0.08;
      this.currentY += (this.targetY - this.currentY) * 0.08;
      for (const item of items) {
        const depth = Number(item.dataset['depth']) || 10;
        item.style.transform = `translate3d(${-this.currentX * depth}px,${-this.currentY * depth}px,0)`;
      }
      this.frame = requestAnimationFrame(loop);
    };
    this.frame = requestAnimationFrame(loop);
  }

  ngOnDestroy(): void {
    const stage = this.host.nativeElement as HTMLElement;
    stage.removeEventListener('mousemove', this.onMove);
    stage.removeEventListener('mouseleave', this.onLeave);
    if (this.frame) cancelAnimationFrame(this.frame);
  }
}
