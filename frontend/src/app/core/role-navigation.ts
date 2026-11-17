export type RoleCode = 'ADMIN' | 'ACCOUNTANT' | 'SELLER';
export type SessionRoleCode = RoleCode | 'SUPERADMIN';

export interface NavigationItem {
  readonly label: string;
  readonly path: string;
  readonly roles: readonly RoleCode[];
}

/**
 * Matriz de accesos por rol.
 *
 * Reglas de negocio de GIOVA:
 * - VENDEDOR: es el único que cotiza. Necesita clientes, precios de referencia y cotizaciones.
 * - CONTADOR: registra facturas (XML), órdenes, actas y garantías; alimenta los precios de compra.
 * - ADMINISTRADOR: supervisa. Ve las cotizaciones que hacen los vendedores (solo lectura),
 *   administra el catálogo (productos, marcas, categorías) y revisa auditoría y reportes.
 *   No cotiza y no usa las herramientas de costeo del vendedor.
 */
export const NAVIGATION_ITEMS: readonly NavigationItem[] = [
  { label: 'Inicio', path: '/operaciones', roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'] },
  {
    label: 'Clientes',
    path: '/operaciones/ventas/clientes',
    roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'],
  },
  {
    // Herramienta de costeo del vendedor, alimentada por las facturas del contador.
    label: 'Precios',
    path: '/operaciones/ventas/precios',
    roles: ['ACCOUNTANT', 'SELLER'],
  },
  {
    // El administrador y el contador entran en modo consulta: ven lo que cotizó el vendedor.
    label: 'Cotizaciones',
    path: '/operaciones/ventas/cotizaciones',
    roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'],
  },
  {
    label: 'Licitaciones',
    path: '/operaciones/licitaciones',
    roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'],
  },
  {
    label: 'Facturas',
    path: '/operaciones/contabilidad/facturas',
    roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'],
  },
  {
    label: 'Órdenes',
    path: '/operaciones/contabilidad/ordenes',
    roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'],
  },
  {
    label: 'Actas',
    path: '/operaciones/contabilidad/entregas',
    roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'],
  },
  {
    label: 'Garantías',
    path: '/operaciones/contabilidad/garantias',
    roles: ['ACCOUNTANT', 'SELLER'],
  },
  {
    label: 'Tareas',
    path: '/operaciones/trabajo/tareas',
    roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'],
  },
  { label: 'Metas', path: '/operaciones/trabajo/metas', roles: ['ADMIN', 'ACCOUNTANT', 'SELLER'] },
  { label: 'Auditoría', path: '/operaciones/trabajo/auditoria', roles: ['ADMIN'] },
  { label: 'Reportes', path: '/operaciones/trabajo/reportes', roles: ['ADMIN', 'ACCOUNTANT'] },
  {
    label: 'Productos',
    path: '/operaciones/catalogo/productos',
    roles: ['ADMIN'],
  },
  { label: 'Marcas', path: '/operaciones/catalogo/marcas', roles: ['ADMIN'] },
  { label: 'Categorías', path: '/operaciones/catalogo/categorias', roles: ['ADMIN'] },
  { label: 'Usuarios', path: '/operaciones/administracion/usuarios', roles: ['ADMIN'] },
];

export function navigationForRole(
  role: SessionRoleCode | null | undefined,
): readonly NavigationItem[] {
  return role === 'SUPERADMIN'
    ? []
    : NAVIGATION_ITEMS.filter((item) => item.roles.includes(role as RoleCode));
}
