import { WorkerSession } from './auth.service';
import { mustRedirectToPasswordChange } from './auth.guard';

const session: WorkerSession = {
  id: 3,
  username: 'worker@example.test',
  displayName: 'Worker',
  roleCode: 'SELLER',
  roleLabel: 'Vendedor',
  mustChangePassword: true,
};

describe('mandatory password route', () => {
  it('redirects a temporary-password session away from business pages', () => {
    expect(mustRedirectToPasswordChange(session, '/operaciones/ventas/cotizaciones')).toBe(true);
    expect(mustRedirectToPasswordChange(session, '/plataforma')).toBe(true);
  });

  it('allows the password page and sessions without a forced change', () => {
    expect(mustRedirectToPasswordChange(session, '/trabajadores/cambiar-contrasena')).toBe(false);
    expect(mustRedirectToPasswordChange(session, '/trabajadores/cambiar-contrasena?next=1')).toBe(
      false,
    );
    expect(
      mustRedirectToPasswordChange({ ...session, mustChangePassword: false }, '/operaciones'),
    ).toBe(false);
  });
});
