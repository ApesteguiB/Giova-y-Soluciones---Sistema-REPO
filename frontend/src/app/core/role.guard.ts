import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';
import { SessionRoleCode } from './role-navigation';

export function hasRequiredRole(
  role: SessionRoleCode | null | undefined,
  expectedRoles: readonly SessionRoleCode[],
): boolean {
  return (
    expectedRoles.length === 0 ||
    (role !== null && role !== undefined && expectedRoles.includes(role))
  );
}

export const roleGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const expectedRoles = (route.data?.['roles'] ?? []) as readonly SessionRoleCode[];

  return auth.me().pipe(
    map((session) =>
      hasRequiredRole(session.roleCode, expectedRoles)
        ? true
        : router.createUrlTree(['/403'], { queryParams: { returnUrl: state.url } }),
    ),
    catchError(() =>
      of(
        router.createUrlTree(['/trabajadores/login'], {
          queryParams: { returnUrl: state.url },
        }),
      ),
    ),
  );
};
