import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { ViewportScroller } from '@angular/common';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import {
  provideRouter,
  TitleStrategy,
  withInMemoryScrolling,
  withRouterConfig,
} from '@angular/router';

import { routes } from './app.routes';
import { PublicSiteService } from './core/public-site.service';
import { publicSiteInterceptor } from './core/public-site.interceptor';
import { BrandTitleStrategy } from './core/brand-title.strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      // Necesario para que el enlace "Nosotros" (/#nosotros) desplace a la sección.
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
      withRouterConfig({ onSameUrlNavigation: 'reload' }),
    ),
    // El navbar es sticky (88 px): el salto a un ancla deja ese aire arriba.
    provideAppInitializer(() => {
      inject(ViewportScroller).setOffset([0, 104]);
    }),
    provideHttpClient(
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([publicSiteInterceptor]),
    ),
    // La empresa dueña del dominio se resuelve antes de pintar: así el logo, los
    // colores y el título no cambian delante del visitante.
    provideAppInitializer(() => inject(PublicSiteService).load()),
    { provide: TitleStrategy, useClass: BrandTitleStrategy },
  ],
};
