import { hasRequiredRole } from './role.guard';

describe('roleGuard', () => {
  it('allows only roles declared by a route', () => {
    expect(hasRequiredRole('ADMIN', ['ADMIN'])).toBe(true);
    expect(hasRequiredRole('SELLER', ['ADMIN'])).toBe(false);
  });

  it('denies an unassigned session even when a role is required', () => {
    expect(hasRequiredRole(null, ['ADMIN'])).toBe(false);
    expect(hasRequiredRole(undefined, ['ADMIN'])).toBe(false);
  });

  it('allows public protected-shell routes with no role restriction', () => {
    expect(hasRequiredRole('ACCOUNTANT', [])).toBe(true);
  });

  it('separates platform administration from company roles', () => {
    expect(hasRequiredRole('SUPERADMIN', ['SUPERADMIN'])).toBe(true);
    expect(hasRequiredRole('SUPERADMIN', ['ADMIN', 'ACCOUNTANT', 'SELLER'])).toBe(false);
    expect(hasRequiredRole('ADMIN', ['SUPERADMIN'])).toBe(false);
  });
});
