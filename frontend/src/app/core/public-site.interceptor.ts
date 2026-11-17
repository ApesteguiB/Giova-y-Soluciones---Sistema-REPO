import { HttpInterceptorFn } from '@angular/common/http';
import { publicTenantOverride } from './public-site.service';

/**
 * Reenvía ?empresa= a la API cuando la página se abrió con ese parámetro. En
 * producción nadie lo usa (cada empresa entra por su dominio) y el backend lo
 * ignora salvo desde localhost, así que solo sirve para las demostraciones.
 */
export const publicSiteInterceptor: HttpInterceptorFn = (request, next) => {
  const empresa = publicTenantOverride();
  if (!empresa || !request.url.startsWith('/api/') || request.params.has('empresa')) {
    return next(request);
  }
  return next(request.clone({ params: request.params.set('empresa', empresa) }));
};
