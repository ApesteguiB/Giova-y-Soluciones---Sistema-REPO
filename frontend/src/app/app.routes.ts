import { Routes } from '@angular/router';
import { PublicLayoutComponent } from './layout/public-layout.component';
import { HomePageComponent } from './home/home-page.component';
import { CatalogPageComponent } from './catalog/catalog-page.component';
import { ContactPageComponent } from './contact/contact-page.component';
import { ClaimsPageComponent } from './claims/claims-page.component';
import { LoginPageComponent } from './auth/login-page.component';
import { AccessDeniedComponent } from './auth/access-denied.component';
import { AdminLayoutComponent } from './admin/admin-layout.component';
import { AdminHomeComponent } from './admin/admin-home.component';
import { authGuard } from './core/auth.guard';
import { roleGuard } from './core/role.guard';
import { BrandsPageComponent } from './admin/brands-page.component';
import { CategoriesPageComponent } from './admin/categories-page.component';
import { ProductsPageComponent } from './admin/products-page.component';

export const routes: Routes = [
  {
    path: '',
    component: PublicLayoutComponent,
    children: [
      { path: '', component: HomePageComponent },
      {
        path: 'productos',
        component: CatalogPageComponent,
        title: 'Productos',
      },
      {
        path: 'contacto',
        component: ContactPageComponent,
        title: 'Contacto',
      },
      {
        path: 'reclamaciones',
        component: ClaimsPageComponent,
        title: 'Libro de Reclamaciones',
      },
      {
        path: 'libro-reclamaciones',
        component: ClaimsPageComponent,
        title: 'Libro de Reclamaciones',
      },
    ],
  },
  { path: 'trabajadores/login', component: LoginPageComponent, title: 'Acceso interno' },
  {
    path: 'trabajadores/cambiar-contrasena',
    loadComponent: () =>
      import('./auth/change-password-page.component').then(
        (module) => module.ChangePasswordPageComponent,
      ),
    canActivate: [authGuard],
    title: 'Cambiar contraseña',
  },
  {
    path: 'plataforma',
    loadComponent: () =>
      import('./platform/platform-page.component').then((module) => module.PlatformPageComponent),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SUPERADMIN'] },
    title: 'Administración de plataforma',
  },
  {
    path: 'operaciones',
    component: AdminLayoutComponent,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'] },
    children: [
      { path: '', component: AdminHomeComponent, title: 'Panel' },
      {
        path: 'administracion/usuarios',
        loadComponent: () =>
          import('./admin/company-users-page.component').then(
            (module) => module.CompanyUsersPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        title: 'Usuarios de la empresa',
      },
      {
        path: 'catalogo/marcas',
        component: BrandsPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        title: 'Marcas',
      },
      {
        path: 'catalogo/categorias',
        component: CategoriesPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        title: 'Categorías',
      },
      {
        path: 'catalogo/productos',
        component: ProductsPageComponent,
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        title: 'Productos',
      },
      {
        path: 'ventas/clientes',
        loadComponent: () =>
          import('./sales/customers-page.component').then(
            (module) => module.CustomersPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'] },
        title: 'Clientes',
      },
      {
        path: 'ventas/precios',
        loadComponent: () =>
          import('./sales/price-references-page.component').then(
            (module) => module.PriceReferencesPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ACCOUNTANT', 'SELLER'] },
        title: 'Precios de referencia',
      },
      {
        path: 'ventas/cotizaciones',
        loadComponent: () =>
          import('./sales/quotations-page.component').then(
            (module) => module.QuotationsPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'] },
        title: 'Cotizaciones',
      },
      {
        path: 'licitaciones',
        loadComponent: () =>
          import('./tenders/tenders-page.component').then((module) => module.TendersPageComponent),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'] },
        title: 'Radar de licitaciones',
      },
      {
        path: 'contabilidad/facturas',
        loadComponent: () =>
          import('./accounting/accounting-page.component').then(
            (module) => module.AccountingPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'], accountingSection: 'invoices' },
        title: 'Facturas internas',
      },
      {
        path: 'contabilidad/ordenes',
        loadComponent: () =>
          import('./accounting/accounting-page.component').then(
            (module) => module.AccountingPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'], accountingSection: 'orders' },
        title: 'Órdenes de compra',
      },
      {
        path: 'contabilidad/entregas',
        loadComponent: () =>
          import('./accounting/accounting-page.component').then(
            (module) => module.AccountingPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'], accountingSection: 'deliveries' },
        title: 'Actas de entrega',
      },
      {
        path: 'contabilidad/garantias',
        loadComponent: () =>
          import('./accounting/accounting-page.component').then(
            (module) => module.AccountingPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ACCOUNTANT', 'SELLER'], accountingSection: 'warranties' },
        title: 'Garantías',
      },
      {
        path: 'trabajo/tareas',
        loadComponent: () =>
          import('./operations/operations-work-page.component').then(
            (module) => module.OperationsWorkPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'], operationsSection: 'tasks' },
        title: 'Tareas',
      },
      {
        path: 'trabajo/metas',
        loadComponent: () =>
          import('./operations/operations-work-page.component').then(
            (module) => module.OperationsWorkPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'], operationsSection: 'goals' },
        title: 'Metas',
      },
      {
        path: 'trabajo/auditoria',
        loadComponent: () =>
          import('./operations/operations-work-page.component').then(
            (module) => module.OperationsWorkPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'], operationsSection: 'audit' },
        title: 'Auditoría',
      },
      {
        path: 'trabajo/reportes',
        loadComponent: () =>
          import('./operations/operations-work-page.component').then(
            (module) => module.OperationsWorkPageComponent,
          ),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'ACCOUNTANT'], operationsSection: 'reports' },
        title: 'Reportes internos',
      },
    ],
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: '/operaciones' },
      { path: 'marcas', pathMatch: 'full', redirectTo: '/operaciones/catalogo/marcas' },
      { path: 'categorias', pathMatch: 'full', redirectTo: '/operaciones/catalogo/categorias' },
      { path: 'productos', pathMatch: 'full', redirectTo: '/operaciones/catalogo/productos' },
      { path: 'clientes', pathMatch: 'full', redirectTo: '/operaciones/ventas/clientes' },
      { path: 'precios', pathMatch: 'full', redirectTo: '/operaciones/ventas/precios' },
      { path: 'cotizaciones', pathMatch: 'full', redirectTo: '/operaciones/ventas/cotizaciones' },
      { path: 'licitaciones', pathMatch: 'full', redirectTo: '/operaciones/licitaciones' },
      { path: 'facturas', pathMatch: 'full', redirectTo: '/operaciones/contabilidad/facturas' },
      { path: 'ordenes', pathMatch: 'full', redirectTo: '/operaciones/contabilidad/ordenes' },
      { path: 'entregas', pathMatch: 'full', redirectTo: '/operaciones/contabilidad/entregas' },
      { path: 'garantias', pathMatch: 'full', redirectTo: '/operaciones/contabilidad/garantias' },
      { path: 'tareas', pathMatch: 'full', redirectTo: '/operaciones/trabajo/tareas' },
      { path: 'metas', pathMatch: 'full', redirectTo: '/operaciones/trabajo/metas' },
      { path: 'auditoria', pathMatch: 'full', redirectTo: '/operaciones/trabajo/auditoria' },
      { path: 'reportes', pathMatch: 'full', redirectTo: '/operaciones/trabajo/reportes' },
    ],
  },
  { path: '403', component: AccessDeniedComponent, title: 'Acceso restringido' },
  { path: '**', redirectTo: '' },
];
