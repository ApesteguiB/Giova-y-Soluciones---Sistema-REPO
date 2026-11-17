import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { CsrfService } from './csrf.service';

export interface AdminBrand {
  id: number;
  name: string;
  slug: string;
  logoUrl: string | null;
  displayColor: string | null;
  featured: boolean;
  displayOrder: number;
  active: boolean;
  version: number;
}
export interface AdminCategory {
  id: number;
  name: string;
  slug: string;
  iconKey: string;
  displayOrder: number;
  active: boolean;
  version: number;
}
export interface AdminProduct {
  id: number;
  slug: string;
  sku: string;
  name: string;
  description: string;
  brandId: number;
  brandName: string;
  categoryId: number;
  categoryName: string;
  badge: string | null;
  imageUrl: string | null;
  displayOrder: number;
  active: boolean;
  version: number;
}
export interface AdminPage<T> {
  items: T[];
  totalItems: number;
  page: number;
  size: number;
  totalPages: number;
}
export interface BrandInput {
  name: string;
  slug: string;
  logoUrl: string;
  displayColor: string;
  featured: boolean;
  displayOrder: number;
}
export interface CategoryInput {
  name: string;
  slug: string;
  iconKey: string;
  displayOrder: number;
}
export interface ProductInput {
  slug: string;
  sku: string;
  name: string;
  description: string;
  brandId: number;
  categoryId: number;
  badge: string;
  imageUrl: string;
  displayOrder: number;
}

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private write<T>(request: () => Observable<T>): Observable<T> {
    return this.csrf.ensureToken().pipe(switchMap(() => request()));
  }
  brands(query = ''): Observable<AdminBrand[]> {
    let params = new HttpParams();
    if (query.trim()) params = params.set('q', query.trim());
    return this.http.get<AdminBrand[]>('/api/v1/admin/brands', { params });
  }
  categories(query = ''): Observable<AdminCategory[]> {
    let params = new HttpParams();
    if (query.trim()) params = params.set('q', query.trim());
    return this.http.get<AdminCategory[]>('/api/v1/admin/categories', { params });
  }
  products(
    query: { q?: string; active?: boolean; brandId?: number; categoryId?: number } = {},
  ): Observable<AdminPage<AdminProduct>> {
    let params = new HttpParams().set('size', '100');
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.active !== undefined) params = params.set('active', query.active);
    if (query.brandId) params = params.set('brandId', query.brandId);
    if (query.categoryId) params = params.set('categoryId', query.categoryId);
    return this.http.get<AdminPage<AdminProduct>>('/api/v1/admin/products', { params });
  }
  createBrand(input: BrandInput) {
    return this.write(() => this.http.post<AdminBrand>('/api/v1/admin/brands', input));
  }
  updateBrand(id: number, input: BrandInput & { version: number }) {
    const { slug: _slug, ...body } = input;
    return this.write(() => this.http.put<AdminBrand>(`/api/v1/admin/brands/${id}`, body));
  }
  setBrandStatus(item: AdminBrand, active: boolean) {
    return this.write(() =>
      this.http.patch<AdminBrand>(`/api/v1/admin/brands/${item.id}/status`, {
        active,
        version: item.version,
      }),
    );
  }
  createCategory(input: CategoryInput) {
    return this.write(() => this.http.post<AdminCategory>('/api/v1/admin/categories', input));
  }
  updateCategory(id: number, input: CategoryInput & { version: number }) {
    const { slug: _slug, ...body } = input;
    return this.write(() => this.http.put<AdminCategory>(`/api/v1/admin/categories/${id}`, body));
  }
  setCategoryStatus(item: AdminCategory, active: boolean) {
    return this.write(() =>
      this.http.patch<AdminCategory>(`/api/v1/admin/categories/${item.id}/status`, {
        active,
        version: item.version,
      }),
    );
  }
  createProduct(input: ProductInput) {
    return this.write(() => this.http.post<AdminProduct>('/api/v1/admin/products', input));
  }
  updateProduct(id: number, input: ProductInput & { version: number }) {
    const { slug: _slug, ...body } = input;
    return this.write(() => this.http.put<AdminProduct>(`/api/v1/admin/products/${id}`, body));
  }
  setProductStatus(item: AdminProduct, active: boolean) {
    return this.write(() =>
      this.http.patch<AdminProduct>(`/api/v1/admin/products/${item.id}/status`, {
        active,
        version: item.version,
      }),
    );
  }
}
