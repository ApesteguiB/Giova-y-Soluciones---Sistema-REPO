import { Component, inject } from '@angular/core';
import { PublicSiteService } from '../core/public-site.service';
import { CompanyHomeComponent } from './company-home.component';
import { GiovaHomeComponent } from './giova-home.component';

/**
 * Elige la portada según la empresa dueña del dominio. GIOVA conserva su
 * portada hecha a medida; el resto usa la plantilla por datos.
 */
@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [GiovaHomeComponent, CompanyHomeComponent],
  template: `
    @switch (site.template()) {
      @case ('GIOVA') {
        <app-giova-home />
      }
      @default {
        <app-company-home />
      }
    }
  `,
})
export class HomePageComponent {
  readonly site = inject(PublicSiteService);
}
