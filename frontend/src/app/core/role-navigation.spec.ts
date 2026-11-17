import { navigationForRole } from './role-navigation';

describe('role navigation', () => {
  it('exposes catalog management only to administrators', () => {
    expect(navigationForRole('ADMIN').map((item) => item.label)).toEqual([
      'Inicio',
      'Clientes',
      'Cotizaciones',
      'Licitaciones',
      'Facturas',
      'Órdenes',
      'Actas',
      'Tareas',
      'Metas',
      'Auditoría',
      'Reportes',
      'Productos',
      'Marcas',
      'Categorías',
      'Usuarios',
    ]);
    expect(navigationForRole('SELLER').map((item) => item.label)).toEqual([
      'Inicio',
      'Clientes',
      'Precios',
      'Cotizaciones',
      'Licitaciones',
      'Facturas',
      'Órdenes',
      'Actas',
      'Garantías',
      'Tareas',
      'Metas',
    ]);
    expect(navigationForRole('ACCOUNTANT').map((item) => item.label)).toEqual([
      'Inicio',
      'Clientes',
      'Precios',
      'Cotizaciones',
      'Licitaciones',
      'Facturas',
      'Órdenes',
      'Actas',
      'Garantías',
      'Tareas',
      'Metas',
      'Reportes',
    ]);
  });

  it('fails closed when a session has no role', () => {
    expect(navigationForRole(null)).toEqual([]);
    expect(navigationForRole(undefined)).toEqual([]);
  });

  it('keeps platform administrators outside the company operations menu', () => {
    expect(navigationForRole('SUPERADMIN')).toEqual([]);
  });
});
