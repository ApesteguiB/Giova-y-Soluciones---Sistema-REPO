import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { EMPTY, expand, map, Observable, reduce } from 'rxjs';

export interface Brand {
  id: string;
  name: string;
  slug: string;
  logoUrl: string | null;
  displayColor: string | null;
  featured: boolean;
  displayOrder: number;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  iconKey: string;
  displayOrder: number;
  productCount: number;
}

export interface Product {
  id: string;
  slug: string;
  sku: string;
  name: string;
  description: string;
  badge: string | null;
  imageUrl: string | null;
  brand: { id: string; name: string; slug: string };
  category: { id: string; name: string; slug: string; iconKey: string };
}

export interface ProductPage {
  items: Product[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface CatalogQuery {
  q?: string;
  category?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class CatalogApiService {
  private readonly http = inject(HttpClient);

  products(query: CatalogQuery = {}): Observable<ProductPage> {
    let params = new HttpParams();
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.category && query.category !== 'all') params = params.set('category', query.category);
    if (query.page !== undefined) params = params.set('page', query.page);
    if (query.size !== undefined) params = params.set('size', query.size);
    return this.http.get<ProductPage>('/api/v1/catalog/products', { params });
  }

  /**
   * Trae el catálogo completo.
   *
   * La API limita cada página a 100 registros (pedir más devuelve 400), así que
   * aquí se recorren las páginas y se concatenan. Lo usan los buscadores que
   * filtran en el navegador mientras el usuario escribe.
   */
  allProducts(pageSize = 100): Observable<Product[]> {
    const size = Math.min(Math.max(pageSize, 1), 100);
    return this.products({ page: 0, size }).pipe(
      expand((page) =>
        page.page + 1 < page.totalPages && page.items.length > 0
          ? this.products({ page: page.page + 1, size })
          : EMPTY,
      ),
      map((page) => page.items),
      reduce((all: Product[], items: Product[]) => all.concat(items), []),
    );
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/v1/catalog/categories');
  }

  brands(featured = false): Observable<Brand[]> {
    let params = new HttpParams();
    if (featured) params = params.set('featured', true);
    return this.http.get<Brand[]>('/api/v1/catalog/brands', { params });
  }
}
