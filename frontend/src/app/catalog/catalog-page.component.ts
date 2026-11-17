import {
  Component,
  HostListener,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Subject,
  Subscription,
  catchError,
  debounceTime,
  distinctUntilChanged,
  of,
  switchMap,
} from 'rxjs';
import {
  CatalogApiService,
  CatalogQuery,
  Category,
  Product,
  ProductPage,
} from '../core/catalog-api.service';
import { PublicSiteService } from '../core/public-site.service';
import { ProductIconComponent } from '../shared/product-icon.component';

const PAGE_SIZE = 60;

@Component({
  selector: 'app-catalog-page',
  standalone: true,
  imports: [ProductIconComponent, RouterLink],
  templateUrl: './catalog-page.component.html',
  styleUrl: './catalog-page.component.css',
})
export class CatalogPageComponent implements OnInit, OnDestroy {
  private readonly api = inject(CatalogApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly searchChanges = new Subject<string>();
  private readonly queryChanges = new Subject<CatalogQuery>();
  private readonly subscription = new Subscription();
  private lastFocused: HTMLElement | null = null;
  /** Última búsqueda que este componente empujó al URL: evita pisar lo que el usuario sigue escribiendo. */
  private lastNavigatedSearch: string | null = null;

  readonly site = inject(PublicSiteService);
  /** Sin número de WhatsApp configurado, el botón lleva al formulario. */
  readonly consultLabel = computed(() =>
    this.site.whatsappLink() ? 'Consultar por WhatsApp' : 'Consultar',
  );
  readonly catalogIntro = computed(() => {
    const tagline = this.site.site().tagline ?? 'Productos y servicios de la empresa';
    return this.site.whatsappLink()
      ? `${tagline}. Filtre por categoría o busque por modelo, y consulte cualquier producto directamente por WhatsApp.`
      : `${tagline}. Filtre por categoría o busque por modelo, y pida la cotización desde la ficha del producto.`;
  });
  readonly priceNote = computed(() =>
    this.site.whatsappLink()
      ? 'Precios y stock: consulte por WhatsApp'
      : 'Precios y stock: consulte por el formulario de contacto',
  );
  readonly categories = signal<Category[]>([]);
  readonly result = signal<ProductPage>({
    items: [],
    totalItems: 0,
    page: 0,
    size: PAGE_SIZE,
    totalPages: 0,
  });
  readonly search = signal('');
  readonly selectedCategory = signal('all');
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly selectedProduct = signal<Product | null>(null);

  /** Total real del catálogo (suma de categorías), no el total filtrado. */
  readonly catalogTotal = computed(() =>
    this.categories().reduce((total, category) => total + category.productCount, 0),
  );

  private readonly brokenImages = signal(new Set<string>());

  ngOnInit(): void {
    this.subscription.add(
      this.api.categories().subscribe({ next: (categories) => this.categories.set(categories) }),
    );
    this.subscription.add(
      this.queryChanges
        .pipe(
          switchMap((query) => {
            this.loading.set(true);
            this.failed.set(false);
            return this.api.products(query).pipe(
              catchError(() => {
                this.failed.set(true);
                return of(null);
              }),
            );
          }),
        )
        .subscribe((result) => {
          if (result) this.result.set(result);
          this.loading.set(false);
        }),
    );
    this.subscription.add(
      this.route.queryParamMap.subscribe((params) => {
        const query = params.get('q') || '';
        // Solo sincronizamos el input cuando el cambio no viene de lo que el usuario acaba de escribir.
        if (query !== this.lastNavigatedSearch) this.search.set(query);
        this.lastNavigatedSearch = null;
        this.selectedCategory.set(params.get('cat') || 'all');
        this.queryChanges.next({
          q: query,
          category: this.selectedCategory(),
          page: Number(params.get('page') || 0),
          size: PAGE_SIZE,
        });
      }),
    );
    this.subscription.add(
      this.searchChanges.pipe(debounceTime(250), distinctUntilChanged()).subscribe((value) => {
        this.lastNavigatedSearch = value.trim();
        void this.router.navigate(['/productos'], {
          queryParams: {
            q: value.trim() || null,
            cat: this.selectedCategory() === 'all' ? null : this.selectedCategory(),
          },
        });
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  onSearch(value: string): void {
    this.search.set(value);
    this.searchChanges.next(value);
  }

  clearSearch(): void {
    this.onSearch('');
  }

  selectCategory(slug: string): void {
    // La página vuelve al inicio al cambiar de categoría.
    void this.router.navigate(['/productos'], {
      queryParams: { q: this.search().trim() || null, cat: slug === 'all' ? null : slug },
    });
  }

  categoryCount(slug: string): number {
    if (slug === 'all') return this.catalogTotal();
    return this.categories().find((category) => category.slug === slug)?.productCount ?? 0;
  }

  openProduct(product: Product, trigger: HTMLElement): void {
    this.lastFocused = trigger;
    this.selectedProduct.set(product);
    document.body.style.overflow = 'hidden';
    setTimeout(() => document.querySelector<HTMLElement>('.modal-close')?.focus());
  }

  closeProduct(): void {
    this.selectedProduct.set(null);
    document.body.style.overflow = '';
    this.lastFocused?.focus();
    this.lastFocused = null;
  }

  productLink(product: Product): string | null {
    return this.site.productWhatsappLink(product.name, product.sku);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.selectedProduct()) this.closeProduct();
  }

  hasPhoto(product: Product): boolean {
    return !!product.imageUrl && !this.brokenImages().has(product.id);
  }

  onImageError(product: Product): void {
    this.brokenImages.update((broken) => new Set(broken).add(product.id));
  }

  previousPage(): void {
    if (this.result().page > 0) this.changePage(this.result().page - 1);
  }

  nextPage(): void {
    if (this.result().page + 1 < this.result().totalPages) this.changePage(this.result().page + 1);
  }

  private changePage(page: number): void {
    void this.router.navigate(['/productos'], {
      queryParams: {
        q: this.search().trim() || null,
        cat: this.selectedCategory() === 'all' ? null : this.selectedCategory(),
        page: page || null,
      },
    });
  }

  load(): void {
    const params = this.route.snapshot.queryParamMap;
    this.queryChanges.next({
      q: this.search(),
      category: this.selectedCategory(),
      page: Number(params.get('page') || 0),
      size: PAGE_SIZE,
    });
  }
}
