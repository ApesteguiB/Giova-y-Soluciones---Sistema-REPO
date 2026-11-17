import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Brand, CatalogApiService, Category, Product } from '../core/catalog-api.service';
import { PublicSiteService } from '../core/public-site.service';
import { ParallaxDirective } from '../shared/parallax.directive';
import { ProductIconComponent } from '../shared/product-icon.component';
import { RevealDirective } from '../shared/reveal.directive';

/**
 * Portada para cualquier empresa distinta de GIOVA: todo el contenido sale del
 * sitio resuelto por dominio y de su propio catálogo, así que una tercera
 * empresa no necesita código nuevo.
 */
@Component({
  selector: 'app-company-home',
  standalone: true,
  imports: [RouterLink, RevealDirective, ParallaxDirective, ProductIconComponent],
  templateUrl: './company-home.component.html',
  styleUrl: './company-home.component.css',
})
export class CompanyHomeComponent implements OnInit {
  private readonly api = inject(CatalogApiService);
  readonly site = inject(PublicSiteService);

  readonly brands = signal<Brand[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly products = signal<Product[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);

  readonly steps = [
    {
      title: 'Levantamiento',
      detail: 'Visita o reunión para medir el alcance real: puntos, equipos y plazos.',
    },
    {
      title: 'Propuesta',
      detail: 'Cotización con marcas y modelos concretos, tiempos de entrega y garantía.',
    },
    {
      title: 'Ejecución',
      detail: 'Instalación y configuración coordinadas con el área usuaria.',
    },
    {
      title: 'Soporte',
      detail: 'Acompañamiento posterior y atención de incidencias dentro de la garantía.',
    },
  ];

  readonly heroBadge = computed(() => this.site.site().footerNote ?? 'Servicios de TI');

  readonly chips = computed(() => this.categories().map((category) => category.name));

  readonly hasContactData = computed(() => {
    const site = this.site.site();
    return !!(site.phone || site.email || site.address || site.hours);
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    forkJoin({
      brands: this.api.brands(true),
      categories: this.api.categories(),
      products: this.api.products({ size: 8 }),
    }).subscribe({
      next: (data) => {
        this.brands.set(data.brands);
        this.categories.set(data.categories);
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

  private readonly brokenImages = signal(new Set<string>());
}
