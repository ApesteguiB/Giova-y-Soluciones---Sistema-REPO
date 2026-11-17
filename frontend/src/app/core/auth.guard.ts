import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';
import { WorkerSession } from './auth.service';

export function mustRedirectToPasswordChange(session: WorkerSession, url: string): boolean {
  const passwordRoute = '/trabajadores/cambiar-contrasena';
  const isPasswordRoute = url === passwordRoute || url.startsWith(`${passwordRoute}?`);
  return session.mustChangePassword && !isPasswordRoute;
}

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.me().pipe(
    map((session) => {
      if (mustRedirectToPasswordChange(session, state.url)) {
        return router.createUrlTree(['/trabajadores/cambiar-contrasena']);
      }
      return true;
    }),
    catchError(() =>
      of(
        router.createUrlTree(['/trabajadores/login'], {
          queryParams: {
            returnUrl:
              state.url.startsWith('/operaciones') ||
              state.url.startsWith('/admin') ||
              state.url.startsWith('/plataforma')
                ? state.url
                : '/operaciones',
          },
        }),
      ),
    ),
  );
};
