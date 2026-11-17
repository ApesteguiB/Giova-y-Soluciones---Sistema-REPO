import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Brand, CatalogApiService, Product } from '../core/catalog-api.service';
import { PublicSiteService } from '../core/public-site.service';
import { CountUpDirective } from '../shared/count-up.directive';
import { ParallaxDirective } from '../shared/parallax.directive';
import { ProductIconComponent } from '../shared/product-icon.component';
import { RevealDirective } from '../shared/reveal.directive';
import { TiltStageDirective } from '../shared/tilt-stage.directive';
import { BrandsOrbitComponent } from './brands-orbit.component';

@Component({
  selector: 'app-giova-home',
  standalone: true,
  imports: [
    RouterLink,
    RevealDirective,
    CountUpDirective,
    ParallaxDirective,
    TiltStageDirective,
    ProductIconComponent,
    BrandsOrbitComponent,
  ],
  templateUrl: './giova-home.component.html',
  styleUrl: './giova-home.component.css',
})
export class GiovaHomeComponent implements OnInit {
  private readonly api = inject(CatalogApiService);
  private readonly router = inject(Router);

  readonly site = inject(PublicSiteService);
  readonly whatsappLink = this.site.whatsappLink;
  readonly brands = signal<Brand[]>([]);
  readonly products = signal<Product[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);

  private readonly brokenImages = signal(new Set<string>());

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    forkJoin({
      brands: this.api.brands(true),
      products: this.api.products({ size: 8 }),
    }).subscribe({
      next: (data) => {
        this.brands.set(data.brands);
        this.products.set(data.products.items);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      },
    });
  }

  hasPhoto(product: Product): boolean {
    return !!product.imageUrl && !this.brokenImages().has(product.id);
  }

  onImageError(product: Product): void {
    this.brokenImages.update((broken) => new Set(broken).add(product.id));
  }

  goToQuote(): void {
    void this.router.navigate(['/contacto'], {
      queryParams: { asunto: 'Cotización de productos' },
      fragment: 'formulario',
    });
  }
}
